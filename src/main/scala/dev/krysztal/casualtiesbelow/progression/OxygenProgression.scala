package dev.krysztal.casualtiesbelow.progression

import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.body.VitalsComponent
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

/** Couples vanilla breath, custom blood volume, blood oxygen, and consciousness.
  *
  * Blood oxygen is a stored reserve on a 0–100 scale. Its instantaneous target is the product of
  * the player's vanilla air fraction and current blood fraction: vanilla air mechanics therefore
  * retain authority over breathing (including Respiration, Water Breathing, bubble columns, and
  * surface recovery), while blood loss reduces how much oxygen the circulation can carry. A sudden
  * loss of blood clamps the reserve to its new capacity immediately; ordinary deoxygenation and
  * reoxygenation approach the target at configured rates.
  *
  * Hypoxia contributes a delta to consciousness rather than assigning consciousness from oxygen.
  * This leaves room for future independent contributors such as pain shock, temperature, and head
  * trauma. Below the hypoxia threshold the drain scales with the oxygen deficit; above a separate
  * recovery threshold consciousness recovers, with the gap between them preventing threshold
  * jitter.
  */
object OxygenProgression {

  /** Advances blood oxygen and its contribution to consciousness by one server tick. Returns
    * whether either stored value changed.
    */
  def tick(player: ServerPlayer, vitals: VitalsComponent): Boolean = {
    val previousOxygen = vitals.bloodOxygen
    val previousConsciousness = vitals.consciousness

    vitals.bloodOxygen = nextBloodOxygen(player, vitals)
    vitals.consciousness = nextConsciousness(vitals)

    vitals.bloodOxygen != previousOxygen || vitals.consciousness != previousConsciousness
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

  private def nextConsciousness(vitals: VitalsComponent): Double = {
    val current = vitals.consciousness.max(0.0).min(VitalsComponent.MaxValue)
    val hypoxiaThreshold = CasualtiesBelowConfig.BloodOxygenHypoxiaThreshold.get()

    if (hypoxiaThreshold > 0.0 && vitals.bloodOxygen < hypoxiaThreshold) {
      val severity = 1.0 - vitals.bloodOxygen / hypoxiaThreshold
      val drain = CasualtiesBelowConfig.HypoxiaConsciousnessDrainPerTick.get() * severity
      (current - drain).max(0.0)
    } else {
      val recoveryThreshold = math.max(
        hypoxiaThreshold,
        CasualtiesBelowConfig.ConsciousnessRecoveryOxygenThreshold.get()
      )
      if (vitals.bloodOxygen < recoveryThreshold) {
        current
      } else {
        (current + CasualtiesBelowConfig.ConsciousnessRecoveryPerTick.get())
          .min(VitalsComponent.MaxValue)
      }
    }
  }

  private def clampFraction(value: Double): Double = value.max(0.0).min(1.0)
}
