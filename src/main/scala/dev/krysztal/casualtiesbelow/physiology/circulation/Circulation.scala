package dev.krysztal.casualtiesbelow.physiology.circulation

import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowDamageTypes
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.physiology.nutrition.StarvationProgression

/** Single access point for the circulation vital: blood volume, blood oxygen, terminal hypoxia
  * exposure, and totem-driven hemostasis. Everything outside this package that touches the
  * circulation state should go through here.
  *
  * Cross-vital dependencies, declared: nutrition starvation pulses translate into blood loss inside
  * [[tick]] (running them here keeps the zero-blood damage-source priority deterministic when
  * bleeding also applies in the same tick); sepsis compresses the effective blood cap (see
  * [[BloodVolume.effectiveMaximum]]).
  */
private[casualtiesbelow] object Circulation {

  /** @param changed
    *   whether any circulation value changed this tick (a sync is due)
    * @param zeroBlood
    *   whether the player reached zero blood: the fatal hit has already been applied, and the
    *   caller must stop this player's progression pass after syncing
    */
  final case class Outcome(changed: Boolean, zeroBlood: Boolean)

  /** One circulation pass for one player, in domain order: the sepsis-compressed blood cap, then
    * fed regeneration, then starvation pulses, then bleeding drain scaled by totem hemostasis, and
    * finally the zero-blood fatality check. Surviving sepsis leaves the body drained (blood over
    * the cap is lost outright); recovery means eating well.
    */
  def tick(
      player: ServerPlayer,
      vitals: VitalsComponentImpl,
      totalBleeding: Double
  ): Outcome = {
    var changed = false

    val maxBlood = BloodVolume.effectiveMaximum(vitals)
    changed = BloodVolume.clamp(vitals, maxBlood) || changed
    if (player.getFoodData.getFoodLevel >= CasualtiesBelowConfig.FedFoodLevelThreshold.get()) {
      val regenerated = BloodVolume.restore(
        vitals,
        CasualtiesBelowConfig.FedBloodRegenPerTick.get(),
        maxBlood
      )
      changed = regenerated > 0.0 || changed
    }

    // Accepted vanilla starvation pulses are translated first. The final blood check below keeps
    // source priority deterministic if bleeding also applies in this tick.
    val starvation = StarvationProgression.consume(player, vitals, maxBlood)
    changed = starvation.changed || changed

    if (totalBleeding > 0.0) {
      val actualBleeding = totalBleeding * TotemHemostasis.bleedingMultiplier(vitals)
      if (actualBleeding > 0.0) {
        val drained = BloodVolume.drain(vitals, actualBleeding, maxBlood)
        changed = drained > 0.0 || changed
      }
    }
    // The timer is hidden client-side state: advancing it does not force an extra sync. Any blood
    // change already syncs through the changed flag, while persistence always writes the live
    // value.
    TotemHemostasis.tick(vitals)

    // Zero blood is fatal before oxygen can drive consciousness down to the independent knockout
    // threshold. Blood-loss death protection restores blood synchronously in the vanilla totem
    // path; an unrescued player remains at zero and dies normally.
    if (vitals.circulation.bloodVolume <= 0.0) {
      val fatal =
        if (maxBlood <= 0.0) {
          CasualtiesBelowDamageTypes.sepsis(player.level())
        } else if (starvation.reachedZero) {
          CasualtiesBelowDamageTypes.starvation(player.level())
        } else {
          CasualtiesBelowDamageTypes.bloodLoss(player.level())
        }
      player.hurtServer(player.level(), fatal, Float.MaxValue)
      return Outcome(changed, zeroBlood = true)
    }

    Outcome(changed, zeroBlood = false)
  }

  /** Advances blood oxygen: blood volume sets the carrying capacity while fully exhausted vanilla
    * air gates the depletion (see [[OxygenProgression]]).
    */
  def tickOxygen(player: ServerPlayer, vitals: VitalsComponentImpl): OxygenProgressionResult =
    OxygenProgression.tick(player, vitals)

  /** Advances terminal hypoxia exposure; starts only after oxygen has consumed this tick's
    * breathing state (see [[HypoxiaProgression]]).
    */
  def tickHypoxia(vitals: VitalsComponentImpl, respirationFailed: Boolean): HypoxiaStep =
    HypoxiaProgression.tick(vitals, respirationFailed)
}
