package dev.krysztal.casualtiesbelow.physiology.infection

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffects

import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.component.MutableLimbState
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.physiology.dirtiness.Dirtiness
import dev.krysztal.casualtiesbelow.physiology.opioid.OpioidWithdrawal
import dev.krysztal.casualtiesbelow.physiology.temperature.TemperatureCalc

/** Single access point for the infection vital: immune health (a lifestyle stat deliberately
  * decoupled from infection load) and sepsis (the whole-body consequence of infection).
  *
  * Cross-vital dependencies, declared: immune drain reads dirtiness, body temperature deviation and
  * opioid withdrawal; sepsis input is the total limb infection load aggregated by the tick driver.
  */
private[casualtiesbelow] object Infection {

  /** One infection pass for one player: sepsis build/decay from the total infection load, then
    * immune health. Returns whether any value changed (a sync is due).
    */
  def tick(vitals: VitalsComponentImpl, infectionLoad: Double, player: ServerPlayer): Boolean = {
    val sepsisChanged = tickSepsis(vitals, infectionLoad)
    val immuneChanged = tickImmune(vitals, player)
    sepsisChanged || immuneChanged
  }

  /** Sepsis is the whole-body consequence of infection: it builds in proportion to the total
    * infection load (the sum of all limbs' infection progress) and recovers at a fixed rate, so
    * below the break-even load it drains away on its own. Its effect is applied where blood is
    * handled: the effective blood volume cap is compressed linearly with sepsis (see
    * [[CasualtiesBelowConfig.effectiveMaxBloodVolume]]), down to zero — fatal — at full sepsis.
    * Returns whether the value changed.
    */
  private def tickSepsis(vitals: VitalsComponentImpl, infectionLoad: Double): Boolean = {
    val maxLoad = MutableLimbState.MaxValue * BodyPart.values.length
    val gain = CasualtiesBelowConfig.SepsisGainPerTick.get() * infectionLoad / maxLoad
    val next = (vitals.infection.sepsis + gain - CasualtiesBelowConfig.SepsisDecayPerTick.get())
      .max(0.0)
      .min(CasualtiesBelowConfig.MaxSepsis.get())
    if (next == vitals.infection.sepsis) return false

    VitalsMutations.setSepsis(vitals, next)
    true
  }

  /** Immune health is a lifestyle stat deliberately decoupled from infection load. Being well-fed
    * restores it slowly and hunger drains it (thresholds mirror vanilla's regeneration/sprinting
    * cutoffs). Active vanilla Poison adds a continuous drain scaled linearly by effect level,
    * independently of whether a poison damage pulse lands. Returns whether the value changed.
    */
  private def tickImmune(vitals: VitalsComponentImpl, player: ServerPlayer): Boolean = {
    val food = player.getFoodData.getFoodLevel
    val foodDelta: Double =
      if (
        food >= CasualtiesBelowConfig.FedFoodLevelThreshold.get().intValue &&
        !OpioidWithdrawal.isActive(vitals)
      ) {
        CasualtiesBelowConfig.FedImmuneRegenPerTick.get()
      } else if (food < CasualtiesBelowConfig.HungryFoodLevelThreshold.get().intValue) {
        -CasualtiesBelowConfig.HungryImmuneDrainPerTick.get()
      } else {
        0.0
      }

    val poisonDrain = Option(player.getEffect(MobEffects.POISON)).fold(0.0) { effect =>
      CasualtiesBelowConfig.PoisonImmuneDrainPerTick.get() * (effect.getAmplifier + 1)
    }
    val dirtDrain = Dirtiness.immuneDrainPerTick(
      vitals.dirtiness,
      CasualtiesBelowConfig.DirtinessImmuneDrainStart.get(),
      CasualtiesBelowConfig.MaxDirtiness.get(),
      CasualtiesBelowConfig.DirtinessImmuneDrainMaxPerTick.get()
    )
    // Temperature stress: °C outside the penalty band drain immune health per minute, cold harder
    // than heat; the fed-regen/drain additive semantics below stay unchanged.
    val temperatureDrain =
      TemperatureCalc.immuneDrainPerTick(
        TemperatureCalc.coldDeviation(
          vitals.bodyTemperature,
          CasualtiesBelowConfig.PenaltyBandLowCelsius.get()
        ),
        CasualtiesBelowConfig.ColdImmuneDrainPerDegreePerMinute.get()
      ) + TemperatureCalc.immuneDrainPerTick(
        TemperatureCalc.hotDeviation(
          vitals.bodyTemperature,
          CasualtiesBelowConfig.PenaltyBandHighCelsius.get()
        ),
        CasualtiesBelowConfig.HotImmuneDrainPerDegreePerMinute.get()
      )
    val delta = foodDelta - poisonDrain - dirtDrain - temperatureDrain
    if (delta == 0.0) return false

    val next =
      (vitals.infection.immuneHealth + delta)
        .max(0.0)
        .min(CasualtiesBelowConfig.MaxImmuneHealth.get())
    if (next == vitals.infection.immuneHealth) return false

    VitalsMutations.setImmuneHealth(vitals, next)
    true
  }
}
