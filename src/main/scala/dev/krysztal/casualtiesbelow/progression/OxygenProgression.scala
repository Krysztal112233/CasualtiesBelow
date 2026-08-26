package dev.krysztal.casualtiesbelow.progression

import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.body.VitalsComponent
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

/** Couples vanilla breath and custom blood volume into a stored blood-oxygen reserve.
  *
  * Blood volume determines its instantaneous carrying capacity. Vanilla air supply acts as a gate:
  * any positive air lets the reserve recover toward that capacity, while exhausted air (`<= 0`)
  * makes it deplete toward zero. Vanilla air mechanics therefore retain authority over breathing
  * (including Respiration, Water Breathing, bubble columns, and surface recovery), while a sudden
  * loss of blood still clamps the reserve to its new capacity immediately.
  *
  * This object does not write consciousness. [[ConsciousnessProgression]] interprets the stored
  * oxygen as a pressure, so future pain shock, head trauma, or temperature sources can compose
  * without healthy oxygen overwriting them.
  */
object OxygenProgression {

  /** Advances blood oxygen by one server tick and returns whether it changed. Consciousness is
    * advanced separately by [[ConsciousnessProgression]] from this reserve.
    */
  def tick(player: ServerPlayer, vitals: VitalsComponent): Boolean = {
    val previousOxygen = vitals.bloodOxygen
    vitals.bloodOxygen = nextBloodOxygen(player, vitals)
    vitals.bloodOxygen != previousOxygen
  }

  private def nextBloodOxygen(player: ServerPlayer, vitals: VitalsComponent): Double = {
    val maxBloodVolume = CasualtiesBelowConfig.MaxBloodVolume.get()
    val bloodFraction = clampFraction(vitals.bloodVolume / maxBloodVolume)
    val capacity = VitalsComponent.MaxBloodOxygen * bloodFraction

    val hasVanillaAir = player.getMaxAirSupply > 0 && player.getAirSupply > 0
    val target = if (hasVanillaAir) capacity else 0.0

    // Oxygen already carried above the new blood-volume capacity is lost immediately. Once the
    // vanilla bubbles are exhausted, depletion remains gradual; resurfacing likewise restores the
    // reserve at its configured rate instead of snapping it back to capacity.
    val current = vitals.bloodOxygen.max(0.0).min(capacity)
    if (current > target) {
      (current - CasualtiesBelowConfig.BloodOxygenDepletionPerTick.get()).max(target)
    } else {
      (current + CasualtiesBelowConfig.BloodOxygenRecoveryPerTick.get()).min(target)
    }
  }

  private def clampFraction(value: Double): Double = value.max(0.0).min(1.0)
}
