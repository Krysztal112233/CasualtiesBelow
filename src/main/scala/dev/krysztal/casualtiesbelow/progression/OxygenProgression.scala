package dev.krysztal.casualtiesbelow.progression

import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.body.VitalsComponent
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

/** Couples vanilla breath and custom blood volume into a stored blood-oxygen reserve.
  *
  * Its instantaneous target is the product of the player's vanilla air fraction and current blood
  * fraction: vanilla air mechanics therefore retain authority over breathing (including
  * Respiration, Water Breathing, bubble columns, and surface recovery), while blood loss reduces
  * how much oxygen the circulation can carry. A sudden loss of blood clamps the reserve to its new
  * capacity immediately; ordinary deoxygenation and reoxygenation approach the target at configured
  * rates.
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

    val maxAir = player.getMaxAirSupply
    val airFraction =
      if (maxAir <= 0) 0.0
      else clampFraction(player.getAirSupply.toDouble / maxAir.toDouble)
    val target = capacity * airFraction

    // Oxygen already carried above the new blood-volume capacity is lost immediately. Breath-driven
    // changes remain gradual so surfacing does not snap the reserve back to full.
    val current = vitals.bloodOxygen.max(0.0).min(capacity)
    if (current > target) {
      (current - CasualtiesBelowConfig.BloodOxygenDepletionPerTick.get()).max(target)
    } else {
      (current + CasualtiesBelowConfig.BloodOxygenRecoveryPerTick.get()).min(target)
    }
  }

  private def clampFraction(value: Double): Double = value.max(0.0).min(1.0)
}
