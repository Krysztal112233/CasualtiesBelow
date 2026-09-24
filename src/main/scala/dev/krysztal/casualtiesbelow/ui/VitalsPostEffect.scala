package dev.krysztal.casualtiesbelow.ui

import java.util.UUID

import com.mojang.blaze3d.resource.CrossFrameResourcePool
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
import dev.krysztal.casualtiesbelow.api.body.vitals.PainShockStage
import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.sync.GameplayDataSnapshot
import dev.krysztal.casualtiesbelow.physiology.consciousness.Unconsciousness

/** Full-screen vitals effects applied to the world before the GUI. Each gameplay mechanism computes
  * an independent visual channel; the channels are composed into one fullscreen post pass where
  * every visual axis owns its own named uniform block (and shader include), so new axes join
  * without touching the existing ones.
  */
@Environment(EnvType.CLIENT)
object VitalsPostEffect {
  private val ChainId = CasualtiesBelow.ofIdentifier("vitals")

  /** One uniform block per visual axis. The block name must match the group in
    * `post_effect/vitals.json` and the std140 declaration in the axis' shader include; members are
    * written in declaration order. Adding an axis means one entry here plus its own JSON block and
    * shader include — no existing axis changes.
    */
  private val EffectBlocks: Vector[EffectBlock] = Vector(
    EffectBlock("ConsciousnessConfig", Vector(_.darkness, _.blur)),
    EffectBlock("BloodLossConfig", Vector(_.desaturation)),
    EffectBlock("DiscomfortConfig", Vector(_.discomfortVignette)),
    EffectBlock("ShockConfig", Vector(_.shock, _.shockNoise, _.shockTime)),
    EffectBlock("GrimeConfig", Vector(_.grimeVignette)),
    EffectBlock("TemperatureConfig", Vector(_.frost, _.heat, _.heatTime))
  )
  private val buffers = new PostEffectBuffers(
    EffectBlocks.map(block => UniformBlock(block.name, block.members.size))
  )
  private val PulseSpeed = (2.0 * Math.PI / 40.0).toFloat
  private val BlurPerceptionExponent = 0.6
  private val ShockPulseSpeed = (2.0 * Math.PI / 20.0).toFloat
  private val ShockLoadInterpolationTicks = 5.0f
  private val ShockPulseMinimumModulation = 0.05f
  private val ShockTimePeriodTicks = 20000
  private val TemperatureInterpolationTicks = 20.0f

  private var shockInterpolationPlayer: Option[UUID] = None
  private var observedShockLoad = 0.0f
  private var displayedShockLoad = 0.0f
  private var shockInterpolationStart = 0.0f
  private var shockInterpolationProgress = 1.0f

  private var temperatureInterpolationPlayer: Option[UUID] = None
  private var observedBodyTemperature = 37.0f
  private var displayedBodyTemperature = 37.0f
  private var temperatureInterpolationStart = 37.0f
  private var temperatureInterpolationProgress = 1.0f

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
        buffers.forChain(chain).foreach { installed =>
          EffectBlocks.foreach { block =>
            installed
              .get(block.name)
              .foreach(buffer => buffers.write(buffer, block.values(strengths)))
          }
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
        if (vitals.consciousness.unconscious) {
          snapShockLoad(player, vitals.shock.load)
          snapBodyTemperature(player, vitals.bodyTemperature)
          Some(unconsciousStrengths)
        } else if (minecraft.gui.hud.isHidden()) {
          snapShockLoad(player, vitals.shock.load)
          snapBodyTemperature(player, vitals.bodyTemperature)
          None
        } else {
          Some(awakeStrengths(player, vitals, deltaTracker)).filter(hasVisibleEffect)
        }
      case Some(player) =>
        val vitals = CasualtiesBelowComponents.Vitals.get(player)
        snapShockLoad(player, vitals.shock.load)
        snapBodyTemperature(player, vitals.bodyTemperature)
        None
      case None =>
        clearShockInterpolation()
        clearTemperatureInterpolation()
        None
    }
  }

  private def unconsciousStrengths: EffectStrengths = {
    EffectStrengths(
      darkness = 1.0f,
      blur = 0.0f,
      desaturation = 0.0f,
      discomfortVignette = 0.0f,
      shock = 0.0f,
      shockNoise = 0.0f,
      shockTime = 0.0f,
      grimeVignette = 0.0f,
      frost = 0.0f,
      heat = 0.0f,
      heatTime = 0.0f
    )
  }

  private def awakeStrengths(
      player: LocalPlayer,
      vitals: VitalsComponent,
      deltaTracker: DeltaTracker
  ): EffectStrengths = {
    val gameplayData = GameplayDataSnapshot.current
    val consciousness = consciousnessVisual(player, vitals, deltaTracker)
    val discomfort = discomfortVisual(vitals, gameplayData)
    val bloodLoss = bloodLossVisual(vitals, gameplayData)
    val painShock = painShockVisual(player, vitals, gameplayData, deltaTracker)
    // Smooth the 1 Hz-synced body temperature once per frame and share it between the frost and
    // heat ramps — calling the smoother twice would double-advance the interpolation.
    val smoothedTemperature =
      if (CasualtiesBelowConfig.visuals.temperatureOverlayEnabled.get()) {
        smoothedBodyTemperature(player, vitals.bodyTemperature, deltaTracker)
      } else {
        snapBodyTemperature(player, vitals.bodyTemperature)
      }

    EffectStrengths(
      darkness = consciousness.darkness,
      blur = consciousness.blur,
      desaturation = bloodLoss.desaturation,
      discomfortVignette = discomfort.vignette,
      shock = painShock.strength,
      shockNoise = painShock.noise,
      shockTime = painShock.time,
      grimeVignette = grimeVisual(vitals),
      frost = frostVisual(smoothedTemperature),
      heat = heatVisual(smoothedTemperature),
      heatTime = (player.tickCount % ShockTimePeriodTicks) +
        deltaTracker.getGameTimeDeltaPartialTick(true)
    )
  }

  /** Cold-side frost overlay strength from the per-frame smoothed body temperature (the smoothing
    * runs once per frame in [[awakeStrengths]] and is shared with the heat ramp).
    */
  private def frostVisual(smoothedTemperature: Double): Float = {
    TemperatureVisuals
      .frostStrength(
        smoothedTemperature,
        CasualtiesBelowConfig.visuals.frostOverlayStartCelsius.get(),
        CasualtiesBelowConfig.visuals.frostOverlayFullSpanCelsius.get(),
        CasualtiesBelowConfig.visuals.frostOverlayMaxStrength.get()
      )
      .toFloat
  }

  /** Hot-side heat overlay strength (haze + warm tint): the mirror of [[frostVisual]] — the two
    * sides can never be active at once.
    */
  private def heatVisual(smoothedTemperature: Double): Float = {
    TemperatureVisuals
      .heatStrength(
        smoothedTemperature,
        CasualtiesBelowConfig.visuals.heatOverlayStartCelsius.get(),
        CasualtiesBelowConfig.visuals.heatOverlayFullSpanCelsius.get(),
        CasualtiesBelowConfig.visuals.heatOverlayMaxStrength.get()
      )
      .toFloat
  }

  /** Grime vignette: the dirtiness display bands gate presentation only — the ramp starts at the
    * grimy band and reaches the configured opacity at maximum dirtiness. Brown-toned, distinct from
    * the nausea darkening.
    */
  private def grimeVisual(vitals: VitalsComponent): Float = {
    val progress = progressAbove(
      vitals.dirtiness,
      CasualtiesBelowConfig.visuals.dirtinessBandGrimy.get(),
      CasualtiesBelowConfig.dirtiness.maxValue.get()
    )
    CasualtiesBelowConfig.visuals.grimeVignetteMaxOpacity.get().toFloat * progress
  }

  private def consciousnessVisual(
      player: LocalPlayer,
      vitals: VitalsComponent,
      deltaTracker: DeltaTracker
  ): ConsciousnessVisual = {
    val dimThreshold = CasualtiesBelowConfig.vitals.consciousnessDimThreshold.get()
    val progress = Mth.clamp(
      ((dimThreshold - vitals.consciousness.level) / math.max(dimThreshold, 1.0e-6)).toFloat,
      0.0f,
      1.0f
    )
    val pulse =
      if (progress > 0.0f) {
        val time = player.tickCount + deltaTracker.getGameTimeDeltaPartialTick(true)
        0.85f + 0.15f * Mth.sin(time * PulseSpeed)
      } else {
        1.0f
      }
    val maxDimming = CasualtiesBelowConfig.visuals.consciousnessMaxDimOpacity.get().toFloat
    val dimming = maxDimming * progress * pulse
    val incapacitation =
      maxDimming * Unconsciousness.severityOf(vitals.consciousness.level).toFloat

    // Perceptual compensation: the shader's zoom-ghost and double-vision offsets scale linearly
    // with strength, so mid-range strengths read as invisible. Ease the blur curve toward the
    // low end so low-but-conscious levels stay readable.
    val maxBlur = CasualtiesBelowConfig.visuals.consciousnessMaxBlurStrength.get().toFloat
    val blurStrength = math.pow(progress.toDouble, BlurPerceptionExponent).toFloat

    ConsciousnessVisual(
      darkness = math.max(dimming, incapacitation),
      blur = maxBlur * blurStrength * pulse
    )
  }

  private def discomfortVisual(
      vitals: VitalsComponent,
      gameplayData: GameplayDataSnapshot
  ): DiscomfortVisual = {
    val progress = progressAbove(
      vitals.discomfort,
      gameplayData.nauseaThreshold,
      gameplayData.maxDiscomfort
    )
    DiscomfortVisual(
      vignette = CasualtiesBelowConfig.visuals.nauseaVignetteMaxOpacity.get().toFloat * progress
    )
  }

  private def bloodLossVisual(
      vitals: VitalsComponent,
      gameplayData: GameplayDataSnapshot
  ): BloodLossVisual = {
    val desaturationStart = CasualtiesBelowConfig.visuals.bloodDesaturationStartFraction.get()
    val fullDesaturation =
      math.min(CasualtiesBelowConfig.visuals.bloodFullDesaturationFraction.get(), desaturationStart)
    val bloodFraction = vitals.circulation.bloodVolume / gameplayData.maxBloodVolume
    BloodLossVisual(
      desaturation = progressBelow(bloodFraction, desaturationStart, fullDesaturation)
    )
  }

  private def painShockVisual(
      player: LocalPlayer,
      vitals: VitalsComponent,
      gameplayData: GameplayDataSnapshot,
      deltaTracker: DeltaTracker
  ): PainShockVisual = {
    PainShockVisual(
      strength = shockVisualStrength(player, vitals, gameplayData, deltaTracker),
      noise = CasualtiesBelowConfig.visuals.shockVisualNoiseStrength.get().toFloat,
      time = (player.tickCount % VitalsPostEffect.ShockTimePeriodTicks) +
        deltaTracker.getGameTimeDeltaPartialTick(true)
    )
  }

  private def shockVisualStrength(
      player: LocalPlayer,
      vitals: VitalsComponent,
      gameplayData: GameplayDataSnapshot,
      deltaTracker: DeltaTracker
  ): Float = {
    val visualStart = CasualtiesBelowConfig.visuals.shockVisualStartLoad.get().toFloat
    val actualLoad = Mth.clamp(vitals.shock.load.toFloat, 0.0f, 100.0f)
    val warningStage =
      vitals.shock.stage == PainShockStage.Stable ||
        vitals.shock.stage == PainShockStage.Deferred
    if (!warningStage || actualLoad <= visualStart) {
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
    val pulseDepth = CasualtiesBelowConfig.visuals.shockVisualPulseStrength.get().toFloat
    val modulation = Mth.clamp(
      1.0f - pulseDepth * pulseProgress * pulseWave,
      ShockPulseMinimumModulation,
      1.0f
    )
    Mth.clamp(
      CasualtiesBelowConfig.visuals.shockVisualMaxStrength.get().toFloat * progress * modulation,
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

  /** Smooths the 1 Hz-synced body temperature toward its latest observation over
    * [[TemperatureInterpolationTicks]], mirroring the shock-load interpolation so the frost overlay
    * grows continuously instead of stepping once per second.
    */
  private def smoothedBodyTemperature(
      player: LocalPlayer,
      actualTemperature: Double,
      deltaTracker: DeltaTracker
  ): Double = {
    if (!temperatureInterpolationPlayer.contains(player.getUUID)) {
      return snapBodyTemperature(player, actualTemperature)
    }
    val observed = actualTemperature.toFloat
    if (observed != observedBodyTemperature) {
      temperatureInterpolationStart = displayedBodyTemperature
      observedBodyTemperature = observed
      temperatureInterpolationProgress = 0.0f
    }
    if (temperatureInterpolationProgress < 1.0f) {
      temperatureInterpolationProgress = math
        .min(
          1.0f,
          temperatureInterpolationProgress +
            deltaTracker.getRealtimeDeltaTicks() / TemperatureInterpolationTicks
        )
        .toFloat
      val progress = temperatureInterpolationProgress
      val eased = progress * progress * (3.0f - 2.0f * progress)
      displayedBodyTemperature = temperatureInterpolationStart +
        (observedBodyTemperature - temperatureInterpolationStart) * eased
    }
    displayedBodyTemperature.toDouble
  }

  private def snapBodyTemperature(player: LocalPlayer, temperature: Double): Double = {
    temperatureInterpolationPlayer = Some(player.getUUID)
    val value = temperature.toFloat
    observedBodyTemperature = value
    displayedBodyTemperature = value
    temperatureInterpolationStart = value
    temperatureInterpolationProgress = 1.0f
    temperature
  }

  private def clearTemperatureInterpolation(): Unit = {
    temperatureInterpolationPlayer = None
    observedBodyTemperature = 37.0f
    displayedBodyTemperature = 37.0f
    temperatureInterpolationStart = 37.0f
    temperatureInterpolationProgress = 1.0f
  }

  private def hasVisibleEffect(strengths: EffectStrengths): Boolean = {
    strengths.darkness > 0.0f ||
    strengths.blur > 0.0f ||
    strengths.desaturation > 0.0f ||
    strengths.discomfortVignette > 0.0f ||
    strengths.shock > 0.0f ||
    strengths.grimeVignette > 0.0f ||
    strengths.frost > 0.0f ||
    strengths.heat > 0.0f
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

  private final case class ConsciousnessVisual(darkness: Float, blur: Float)

  private final case class DiscomfortVisual(vignette: Float)

  private final case class BloodLossVisual(desaturation: Float)

  private final case class PainShockVisual(strength: Float, noise: Float, time: Float)

  /** A named std140 uniform block owning part of EffectStrengths; members map 1:1 to the block
    * declaration in the axis' shader include and to its `post_effect/vitals.json` entry.
    */
  private final case class EffectBlock(
      name: String,
      members: Vector[EffectStrengths => Float]
  ) {
    def values(strengths: EffectStrengths): Vector[Float] = members.map(_(strengths))
  }

  private final case class EffectStrengths(
      darkness: Float,
      blur: Float,
      desaturation: Float,
      discomfortVignette: Float,
      shock: Float,
      shockNoise: Float,
      shockTime: Float,
      grimeVignette: Float,
      frost: Float,
      heat: Float,
      heatTime: Float
  )
}
