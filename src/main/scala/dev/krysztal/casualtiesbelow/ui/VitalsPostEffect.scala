package dev.krysztal.casualtiesbelow.ui

import java.util.UUID

import scala.jdk.CollectionConverters.*

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.resource.CrossFrameResourcePool
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.LevelTargetBundle
import net.minecraft.client.renderer.PostChain
import net.minecraft.util.Mth

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.PainShockStage
import dev.krysztal.casualtiesbelow.api.body.VitalsComponent
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.consciousness.Unconsciousness
import dev.krysztal.casualtiesbelow.mixin.PostChainAccessor
import dev.krysztal.casualtiesbelow.mixin.PostPassAccessor
import dev.krysztal.casualtiesbelow.sync.GameplayDataSnapshot

import org.lwjgl.system.MemoryStack

/** Full-screen vitals effects applied to the world before the GUI: low consciousness contributes
  * progressive darkening (edge vignette plus uniform haze) with a gentle pulse, zoom blur, and
  * double vision along a single ramp; approaching the consciousness floor fades the world to black;
  * nausea contributes additional steady darkening; low absolute blood volume progressively removes
  * color; accumulating pain-shock load adds a warm peripheral warning with restrained final-stage
  * pulsing and fine animated edge static.
  */
@Environment(EnvType.CLIENT)
object VitalsPostEffect {
  private val ChainId = CasualtiesBelow.ofIdentifier("vitals")
  private val UniformGroup = "VitalsConfig"
  private val UniformBufferSize = new Std140SizeCalculator()
    .putFloat()
    .putFloat()
    .putFloat()
    .putFloat()
    .putFloat()
    .putFloat()
    .get()
  private val UniformBufferUsage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE
  private val PulseSpeed = (2.0 * Math.PI / 40.0).toFloat
  private val ShockPulseSpeed = (2.0 * Math.PI / 20.0).toFloat
  private val ShockLoadInterpolationTicks = 5.0f
  private val ShockPulseMinimumModulation = 0.05f
  private val ShockTimePeriodTicks = 20000

  private var cachedChain: Option[PostChain] = None
  private var cachedConfigBuffer: Option[GpuBuffer] = None
  private var shockInterpolationPlayer: Option[UUID] = None
  private var observedShockLoad = 0.0f
  private var displayedShockLoad = 0.0f
  private var shockInterpolationStart = 0.0f
  private var shockInterpolationProgress = 1.0f

  def render(
      gameRenderer: GameRenderer,
      deltaTracker: DeltaTracker,
      resourcePool: CrossFrameResourcePool
  ): Unit = {
    effectStrengths(deltaTracker).foreach { strengths =>
      Option(
        Minecraft
          .getInstance()
          .getShaderManager()
          .getPostChain(ChainId, LevelTargetBundle.MAIN_TARGETS)
      ).foreach { chain =>
        configBuffer(chain).foreach { buffer =>
          writeStrengths(buffer, strengths)
          chain.process(gameRenderer.mainRenderTarget(), resourcePool)
        }
      }
    }
  }

  private def effectStrengths(deltaTracker: DeltaTracker): Option[EffectStrengths] = {
    val minecraft = Minecraft.getInstance()
    Option(minecraft.player) match {
      case Some(player) if !player.isCreative && !player.isSpectator && player.isAlive =>
        val vitals = CasualtiesBelowComponents.Vitals.get(player)
        if (vitals.unconscious) {
          snapShockLoad(player, vitals.painShockLoad)
          Some(unconsciousStrengths)
        } else if (minecraft.gui.hud.isHidden()) {
          snapShockLoad(player, vitals.painShockLoad)
          None
        } else {
          Some(awakeStrengths(player, vitals, deltaTracker)).filter(hasVisibleEffect)
        }
      case Some(player) =>
        snapShockLoad(player, CasualtiesBelowComponents.Vitals.get(player).painShockLoad)
        None
      case None =>
        clearShockInterpolation()
        None
    }
  }

  private def unconsciousStrengths: EffectStrengths = {
    EffectStrengths(
      darkness = 1.0f,
      blur = 0.0f,
      desaturation = 0.0f,
      shock = 0.0f,
      shockNoise = 0.0f,
      shockTime = 0.0f
    )
  }

  private def awakeStrengths(
      player: LocalPlayer,
      vitals: VitalsComponent,
      deltaTracker: DeltaTracker
  ): EffectStrengths = {
    val dimThreshold = CasualtiesBelowConfig.ConsciousnessDimThreshold.get()
    val consciousnessProgress = Mth.clamp(
      ((dimThreshold - vitals.consciousness) / math.max(dimThreshold, 1.0e-6)).toFloat,
      0.0f,
      1.0f
    )
    val pulse =
      if (consciousnessProgress > 0.0f) {
        val time = player.tickCount + deltaTracker.getGameTimeDeltaPartialTick(true)
        0.85f + 0.15f * Mth.sin(time * PulseSpeed)
      } else {
        1.0f
      }
    val gameplayData = GameplayDataSnapshot.current
    val discomfortDarknessProgress = progressAbove(
      vitals.discomfort,
      gameplayData.nauseaThreshold,
      gameplayData.maxDiscomfort
    )
    val shock = shockVisualStrength(player, vitals, gameplayData, deltaTracker)
    val shockTime =
      (player.tickCount % VitalsPostEffect.ShockTimePeriodTicks) +
        deltaTracker.getGameTimeDeltaPartialTick(true)
    val desaturationStart = CasualtiesBelowConfig.BloodDesaturationStartFraction.get()
    val fullDesaturation =
      math.min(CasualtiesBelowConfig.BloodFullDesaturationFraction.get(), desaturationStart)
    val bloodFraction = vitals.bloodVolume / gameplayData.maxBloodVolume
    val desaturation = progressBelow(bloodFraction, desaturationStart, fullDesaturation)
    val severityDarkness = Unconsciousness.severityOf(vitals.consciousness).toFloat
    val consciousnessDarkness =
      CasualtiesBelowConfig.ConsciousnessMaxDimOpacity.get().toFloat *
        consciousnessProgress * pulse
    val discomfortDarkness =
      CasualtiesBelowConfig.DiscomfortMaxVignetteOpacity.get().toFloat *
        discomfortDarknessProgress
    EffectStrengths(
      darkness = math.max(math.max(consciousnessDarkness, discomfortDarkness), severityDarkness),
      blur = CasualtiesBelowConfig.ConsciousnessMaxBlurStrength.get().toFloat *
        consciousnessProgress * pulse,
      desaturation = desaturation,
      shock = shock,
      shockNoise = CasualtiesBelowConfig.ShockVisualNoiseStrength.get().toFloat,
      shockTime = shockTime
    )
  }

  private def shockVisualStrength(
      player: LocalPlayer,
      vitals: VitalsComponent,
      gameplayData: GameplayDataSnapshot,
      deltaTracker: DeltaTracker
  ): Float = {
    val visualStart = CasualtiesBelowConfig.ShockVisualStartLoad.get().toFloat
    val actualLoad = Mth.clamp(vitals.painShockLoad.toFloat, 0.0f, 100.0f)
    if (vitals.painShockStage != PainShockStage.Stable || actualLoad <= visualStart) {
      snapShockLoad(player, actualLoad)
      return 0.0f
    }

    val collapseThreshold =
      Mth.clamp(gameplayData.shockCollapseThreshold.toFloat, 0.0f, 100.0f)
    val load = smoothedShockLoad(player, actualLoad, deltaTracker)
    val progress = progressAbove(load, visualStart, collapseThreshold)
    val pulseStart = (visualStart + collapseThreshold) * 0.5f
    val pulseProgress = progressAbove(load, pulseStart, collapseThreshold)
    val time = player.tickCount + deltaTracker.getGameTimeDeltaPartialTick(true)
    val pulseWave = 0.5f + 0.5f * Mth.sin(time * ShockPulseSpeed)
    val pulseDepth = CasualtiesBelowConfig.ShockVisualPulseStrength.get().toFloat
    val modulation = Mth.clamp(
      1.0f - pulseDepth * pulseProgress * pulseWave,
      ShockPulseMinimumModulation,
      1.0f
    )
    Mth.clamp(
      CasualtiesBelowConfig.ShockVisualMaxStrength.get().toFloat * progress * modulation,
      0.0f,
      1.0f
    )
  }

  private def smoothedShockLoad(
      player: LocalPlayer,
      actualLoad: Float,
      deltaTracker: DeltaTracker
  ): Float = {
    if (!shockInterpolationPlayer.contains(player.getUUID)) {
      return snapShockLoad(player, actualLoad)
    }
    if (actualLoad != observedShockLoad) {
      shockInterpolationStart = displayedShockLoad
      observedShockLoad = actualLoad
      shockInterpolationProgress = 0.0f
    }
    if (shockInterpolationProgress < 1.0f) {
      shockInterpolationProgress = math
        .min(
          1.0f,
          shockInterpolationProgress +
            deltaTracker.getRealtimeDeltaTicks() / ShockLoadInterpolationTicks
        )
        .toFloat
      val progress = shockInterpolationProgress
      val eased = progress * progress * (3.0f - 2.0f * progress)
      displayedShockLoad =
        shockInterpolationStart + (observedShockLoad - shockInterpolationStart) * eased
    }
    displayedShockLoad
  }

  private def snapShockLoad(player: LocalPlayer, load: Double): Float = {
    val normalizedLoad = Mth.clamp(load.toFloat, 0.0f, 100.0f)
    shockInterpolationPlayer = Some(player.getUUID)
    observedShockLoad = normalizedLoad
    displayedShockLoad = normalizedLoad
    shockInterpolationStart = normalizedLoad
    shockInterpolationProgress = 1.0f
    normalizedLoad
  }

  private def clearShockInterpolation(): Unit = {
    shockInterpolationPlayer = None
    observedShockLoad = 0.0f
    displayedShockLoad = 0.0f
    shockInterpolationStart = 0.0f
    shockInterpolationProgress = 1.0f
  }

  private def hasVisibleEffect(strengths: EffectStrengths): Boolean = {
    strengths.darkness > 0.0f ||
    strengths.blur > 0.0f ||
    strengths.desaturation > 0.0f ||
    strengths.shock > 0.0f
  }

  private def configBuffer(chain: PostChain): Option[GpuBuffer] = {
    val needsInstall = cachedChain.forall(_ ne chain) || cachedConfigBuffer.exists(_.isClosed)
    if (needsInstall) {
      cachedChain = Some(chain)
      cachedConfigBuffer = installConfigBuffer(chain)
    }
    cachedConfigBuffer
  }

  private def installConfigBuffer(chain: PostChain): Option[GpuBuffer] = {
    val uniformMap = chain
      .asInstanceOf[PostChainAccessor]
      .casualtiesbelow$getPasses()
      .asScala
      .iterator
      .map(_.asInstanceOf[PostPassAccessor].casualtiesbelow$getCustomUniforms())
      .find(_.containsKey(UniformGroup))

    uniformMap
      .map { uniforms =>
        val buffer = createConfigBuffer()
        Option(uniforms.put(UniformGroup, buffer)).foreach(_.close())
        buffer
      }
      .orElse {
        CasualtiesBelow.Logger.warn(
          "Post chain {} has no {} uniform buffer; vitals effects are disabled",
          ChainId,
          UniformGroup
        )
        None
      }
  }

  private def createConfigBuffer(): GpuBuffer = {
    val stack = MemoryStack.stackPush()
    try {
      val builder = Std140Builder.onStack(stack, UniformBufferSize)
      builder
        .putFloat(0.0f)
        .putFloat(0.0f)
        .putFloat(0.0f)
        .putFloat(0.0f)
        .putFloat(0.0f)
        .putFloat(0.0f)
      RenderSystem
        .getDevice()
        .createBuffer(
          () => "CasualtiesBelow / vitals effects",
          UniformBufferUsage,
          builder.get()
        )
    } finally {
      stack.close()
    }
  }

  private def writeStrengths(buffer: GpuBuffer, strengths: EffectStrengths): Unit = {
    val view = buffer.map(false, true)
    try {
      Std140Builder
        .intoBuffer(view.data())
        .putFloat(strengths.darkness)
        .putFloat(strengths.blur)
        .putFloat(strengths.desaturation)
        .putFloat(strengths.shock)
        .putFloat(strengths.shockNoise)
        .putFloat(strengths.shockTime)
    } finally {
      view.close()
    }
  }

  private def progressAbove(value: Double, threshold: Double, maximum: Double): Float = {
    if (value < threshold) return 0.0f
    if (maximum <= threshold) return 1.0f

    Mth.clamp(((value - threshold) / (maximum - threshold)).toFloat, 0.0f, 1.0f)
  }

  private def progressBelow(value: Double, start: Double, full: Double): Float = {
    if (value >= start) return 0.0f
    if (full >= start || value <= full) return 1.0f

    Mth.clamp(((start - value) / (start - full)).toFloat, 0.0f, 1.0f)
  }

  private final case class EffectStrengths(
      darkness: Float,
      blur: Float,
      desaturation: Float,
      shock: Float,
      shockNoise: Float,
      shockTime: Float
  )
}
