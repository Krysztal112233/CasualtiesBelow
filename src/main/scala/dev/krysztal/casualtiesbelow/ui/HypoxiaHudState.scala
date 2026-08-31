package dev.krysztal.casualtiesbelow.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

import dev.krysztal.casualtiesbelow.api.body.VitalsComponent
import dev.krysztal.casualtiesbelow.component.ComponentAccess
import dev.krysztal.casualtiesbelow.internal.sync.GameplayDataSnapshot

/** Client-side animation state for the drowning HUD.
  *
  * Server data arrives quantized — blood oxygen on every changing tick, terminal exposure only at
  * roughly one-second milestones — so this layer reconstructs continuous motion. Game-coupled
  * values ease or extrapolate on the client tick and are interpolated per frame with the game
  * partial tick; pure UI fades advance per frame with the realtime delta. Everything freezes while
  * the game is paused, matching the frozen server clock.
  */
@Environment(EnvType.CLIENT)
object HypoxiaHudState {
  private val OxygenSmoothing = 0.22
  private val OxygenSnapEpsilon = 1.0e-3
  private val SecondsPerTick = 1.0 / 20.0
  private val BreathFrequencyHz = 0.14
  private val DirectionEpsilon = 1.0e-6
  private val OxygenFadeTicks = 6.0f
  private val TerminalBlendTicks = 6.0f
  private val DirectionBlendTicks = 5.0f

  private var trackedPlayer: Option[LocalPlayer] = None
  private var previousOxygen = VitalsComponent.MaxBloodOxygen
  private var displayOxygen = VitalsComponent.MaxBloodOxygen
  private var anchoredExposure = -1
  private var previousExposure = 0.0
  private var displayExposure = 0.0
  private var oxygenPulseCycles = 0.0
  private var terminalPulseCycles = 0.0
  private var breathCycles = 0.0
  private var lastConsciousness: Option[Double] = None
  private var directionRising = false
  private var oxygenVisibility = 0.0f
  private var terminalBlend = 0.0f
  private var directionBlend = 0.0f

  def register(): Unit = {
    ClientTickEvents.END_CLIENT_TICK.register(tick)
  }

  /** Smoothed blood oxygen for the current frame. */
  def smoothedOxygen(partialTick: Float): Double = {
    Mth.lerp(partialTick.toDouble, previousOxygen, displayOxygen)
  }

  /** Continuously extrapolated terminal exposure ticks for the current frame. */
  def smoothedExposureTicks(partialTick: Float): Double = {
    Mth.lerp(partialTick.toDouble, previousExposure, displayExposure)
  }

  /** Accumulated oxygen-pulse phase in cycles, extended to the current frame. */
  def oxygenPulsePhaseCycles(partialTick: Float): Double = {
    val fraction = smoothedOxygen(partialTick) / VitalsComponent.MaxBloodOxygen
    oxygenPulseCycles +
      HypoxiaVisuals.oxygenPulseFrequency(fraction) * partialTick.toDouble * SecondsPerTick
  }

  /** Accumulated countdown-pulse phase in cycles, extended to the current frame. */
  def terminalPulsePhaseCycles(partialTick: Float): Double = {
    val remaining = HypoxiaVisuals.terminalRemainingFraction(
      smoothedExposureTicks(partialTick),
      GameplayDataSnapshot.current.terminalHypoxiaDurationTicks
    )
    terminalPulseCycles +
      HypoxiaVisuals.terminalPulseFrequency(remaining) * partialTick.toDouble * SecondsPerTick
  }

  /** Idle wave phase in cycles driving the bubble row's gentle staggered bob. */
  def breathPhaseCycles(partialTick: Float): Double = {
    breathCycles + BreathFrequencyHz * partialTick.toDouble * SecondsPerTick
  }

  /** Eases the mod-bar crossfade toward its target; called once per frame from the air-bar slot. */
  def advanceOxygenVisibility(realtimeDeltaTicks: Float, engaged: Boolean): Unit = {
    oxygenVisibility = approach(
      oxygenVisibility,
      if (engaged) 1.0f else 0.0f,
      realtimeDeltaTicks / OxygenFadeTicks
    )
  }

  /** Eases the wake-ring → countdown-ring crossfade; called once per frame from the overlay. */
  def advanceTerminalBlend(realtimeDeltaTicks: Float, terminalActive: Boolean): Unit = {
    terminalBlend = approach(
      terminalBlend,
      if (terminalActive) 1.0f else 0.0f,
      realtimeDeltaTicks / TerminalBlendTicks
    )
  }

  /** Eases the wake ring's red/white direction tint toward the last observed trend. */
  def advanceDirectionBlend(realtimeDeltaTicks: Float): Unit = {
    directionBlend = approach(
      directionBlend,
      if (directionRising) 1.0f else 0.0f,
      realtimeDeltaTicks / DirectionBlendTicks
    )
  }

  def oxygenVisibilityValue: Float = oxygenVisibility
  def terminalBlendValue: Float = terminalBlend
  def directionBlendValue: Float = directionBlend

  /** Clears overlay-owned transitions when the ring fully hides or the player is untracked. */
  def resetOverlayTransitions(): Unit = {
    terminalBlend = 0.0f
    directionBlend = 0.0f
    directionRising = false
    lastConsciousness = None
  }

  private def tick(minecraft: Minecraft): Unit = {
    Option(minecraft.player).filter(player => !player.isCreative && !player.isSpectator) match {
      case Some(player) =>
        if (!trackedPlayer.contains(player)) {
          snapToPlayer(player)
        }
        if (!minecraft.isPaused) {
          val vitals = ComponentAccess.vitals(player)
          tickOxygen(vitals.bloodOxygen)
          tickExposure(vitals.hypoxiaExposureTicks)
          tickDirection(vitals.consciousness, vitals.hypoxiaExposureTicks > 0)
          tickPhases()
        }
      case None =>
        trackedPlayer = None
        reset()
    }
  }

  private def snapToPlayer(player: LocalPlayer): Unit = {
    val vitals = ComponentAccess.vitals(player)
    trackedPlayer = Some(player)
    previousOxygen = vitals.bloodOxygen
    displayOxygen = vitals.bloodOxygen
    anchoredExposure = vitals.hypoxiaExposureTicks
    previousExposure = vitals.hypoxiaExposureTicks.toDouble
    displayExposure = vitals.hypoxiaExposureTicks.toDouble
    lastConsciousness = Some(vitals.consciousness)
    directionRising = false
  }

  private def tickOxygen(bloodOxygen: Double): Unit = {
    previousOxygen = displayOxygen
    val sane = if (bloodOxygen.isFinite) bloodOxygen else 0.0
    val target = Mth.clamp(sane, 0.0, VitalsComponent.MaxBloodOxygen)
    displayOxygen += (target - displayOxygen) * OxygenSmoothing
    if (math.abs(displayOxygen - target) < OxygenSnapEpsilon) {
      displayOxygen = target
    }
  }

  private def tickExposure(syncedExposure: Int): Unit = {
    previousExposure = displayExposure
    if (syncedExposure != anchoredExposure) {
      // Re-anchor on every sync milestone; the one-tick interpolation absorbs the correction.
      anchoredExposure = syncedExposure
      displayExposure = syncedExposure.toDouble
    } else if (syncedExposure > 0) {
      // The server timer runs at exactly one tick per tick, so local extrapolation is safe
      // between the sparse sync milestones.
      displayExposure += 1.0
    } else {
      displayExposure = 0.0
    }
  }

  private def tickDirection(consciousness: Double, terminalActive: Boolean): Unit = {
    if (!terminalActive) {
      lastConsciousness.foreach { previous =>
        if (consciousness > previous + DirectionEpsilon) {
          directionRising = true
        } else if (consciousness < previous - DirectionEpsilon) {
          directionRising = false
        }
      }
      lastConsciousness = Some(consciousness)
    }
  }

  private def tickPhases(): Unit = {
    val oxygenFraction = displayOxygen / VitalsComponent.MaxBloodOxygen
    oxygenPulseCycles = positiveModulo(
      oxygenPulseCycles + HypoxiaVisuals.oxygenPulseFrequency(oxygenFraction) * SecondsPerTick,
      1.0
    )
    val remaining = HypoxiaVisuals.terminalRemainingFraction(
      displayExposure,
      GameplayDataSnapshot.current.terminalHypoxiaDurationTicks
    )
    terminalPulseCycles = positiveModulo(
      terminalPulseCycles + HypoxiaVisuals.terminalPulseFrequency(remaining) * SecondsPerTick,
      1.0
    )
    breathCycles = positiveModulo(breathCycles + BreathFrequencyHz * SecondsPerTick, 1.0)
  }

  private def approach(current: Float, target: Float, step: Float): Float = {
    if (current < target) math.min(target, current + step)
    else math.max(target, current - step)
  }

  private def reset(): Unit = {
    previousOxygen = VitalsComponent.MaxBloodOxygen
    displayOxygen = VitalsComponent.MaxBloodOxygen
    anchoredExposure = -1
    previousExposure = 0.0
    displayExposure = 0.0
    oxygenPulseCycles = 0.0
    terminalPulseCycles = 0.0
    breathCycles = 0.0
    oxygenVisibility = 0.0f
    resetOverlayTransitions()
  }

  private def positiveModulo(value: Double, modulus: Double): Double = {
    val remainder = value % modulus
    if (remainder < 0.0) remainder + modulus else remainder
  }
}
