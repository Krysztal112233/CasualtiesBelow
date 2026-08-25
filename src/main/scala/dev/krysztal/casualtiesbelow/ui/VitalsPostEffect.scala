package dev.krysztal.casualtiesbelow.ui

import scala.jdk.CollectionConverters.*

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.resource.CrossFrameResourcePool
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.LevelTargetBundle
import net.minecraft.client.renderer.PostChain
import net.minecraft.util.Mth

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.mixin.PostChainAccessor
import dev.krysztal.casualtiesbelow.mixin.PostPassAccessor
import dev.krysztal.casualtiesbelow.sync.GameplayDataSnapshot

import org.lwjgl.system.MemoryStack

/** Full-screen vitals effects applied to the world before the GUI: low consciousness contributes
  * edge darkening, blackout pulsing, zoom blur, and double vision; nausea contributes only steady
  * edge darkening; low absolute blood volume progressively removes color.
  */
@Environment(EnvType.CLIENT)
object VitalsPostEffect {
  private val ChainId = CasualtiesBelow.ofIdentifier("vitals")
  private val UniformGroup = "VitalsConfig"
  private val UniformBufferSize =
    new Std140SizeCalculator().putFloat().putFloat().putFloat().putFloat().get()
  private val UniformBufferUsage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE
  private val PulseSpeed = (2.0 * Math.PI / 40.0).toFloat
  private val BlackoutHazeStrength = (1.0 / 0.35).toFloat

  private var cachedChain: Option[PostChain] = None
  private var cachedConfigBuffer: Option[GpuBuffer] = None

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
    Option(minecraft.player)
      .filter(player => !player.isCreative && !player.isSpectator && player.isAlive)
      .flatMap { player =>
        val vitals = CasualtiesBelowComponents.Vitals.get(player)
        if (vitals.unconscious) {
          // Haze is a whole-scene multiplier in the existing std140 contract; 1 / HazeFraction
          // reaches full black without changing the shader layout or covering the GUI.
          Some(
            EffectStrengths(
              vignette = 1.0f,
              haze = BlackoutHazeStrength,
              blur = 0.0f,
              desaturation = 0.0f
            )
          )
        } else if (minecraft.gui.hud.isHidden()) {
          None
        } else {
          val dimThreshold = CasualtiesBelowConfig.ConsciousnessDimThreshold.get()
          val blackoutThreshold =
            math.min(CasualtiesBelowConfig.ConsciousnessBlackoutThreshold.get(), dimThreshold)
          val pulse =
            if (vitals.consciousness <= blackoutThreshold) {
              val time = player.tickCount + deltaTracker.getGameTimeDeltaPartialTick(true)
              0.85f + 0.15f * Mth.sin(time * PulseSpeed)
            } else {
              1.0f
            }
          val consciousnessVignetteProgress = Mth.clamp(
            ((dimThreshold - vitals.consciousness) / (dimThreshold - blackoutThreshold)
              .max(1.0e-6)).toFloat,
            0.0f,
            1.0f
          )
          val blurProgress =
            if (vitals.consciousness > blackoutThreshold) {
              0.0f
            } else if (blackoutThreshold <= 0.0) {
              1.0f
            } else {
              Mth.clamp(
                ((blackoutThreshold - vitals.consciousness) / blackoutThreshold).toFloat,
                0.0f,
                1.0f
              )
            }
          val gameplayData = GameplayDataSnapshot.current
          val discomfortVignetteProgress = progressAbove(
            vitals.discomfort,
            gameplayData.nauseaThreshold,
            gameplayData.maxDiscomfort
          )
          val desaturationStart = CasualtiesBelowConfig.BloodDesaturationStartFraction.get()
          val fullDesaturation =
            math.min(CasualtiesBelowConfig.BloodFullDesaturationFraction.get(), desaturationStart)
          val bloodFraction = vitals.bloodVolume / gameplayData.maxBloodVolume
          val desaturation = progressBelow(bloodFraction, desaturationStart, fullDesaturation)
          val consciousnessVignette =
            CasualtiesBelowConfig.ConsciousnessMaxDimOpacity.get().toFloat *
              consciousnessVignetteProgress * pulse
          val discomfortVignette =
            CasualtiesBelowConfig.DiscomfortMaxVignetteOpacity.get().toFloat *
              discomfortVignetteProgress
          val strengths = EffectStrengths(
            vignette = math.max(consciousnessVignette, discomfortVignette),
            haze = consciousnessVignette,
            blur = CasualtiesBelowConfig.ConsciousnessMaxBlurStrength.get().toFloat *
              blurProgress * pulse,
            desaturation = desaturation
          )
          Option.when(
            strengths.vignette > 0.0f ||
              strengths.haze > 0.0f ||
              strengths.blur > 0.0f ||
              strengths.desaturation > 0.0f
          )(strengths)
        }
      }
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
      builder.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f)
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
        .putFloat(strengths.vignette)
        .putFloat(strengths.haze)
        .putFloat(strengths.blur)
        .putFloat(strengths.desaturation)
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
      vignette: Float,
      haze: Float,
      blur: Float,
      desaturation: Float
  )
}
