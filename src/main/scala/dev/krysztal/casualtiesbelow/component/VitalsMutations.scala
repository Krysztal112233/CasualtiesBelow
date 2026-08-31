package dev.krysztal.casualtiesbelow.component

import net.minecraft.world.entity.player.Player

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.PainShockStage

/** Internal write authority for the mutable storage behind the public read-only vitals view. */
object VitalsMutations {
  private[casualtiesbelow] def setImmuneHealth(
      vitals: VitalsComponentImpl,
      value: Double
  ): Boolean = {
    val previous = vitals.infection.immuneHealth
    vitals.setImmuneHealth(value)
    vitals.infection.immuneHealth != previous
  }

  private[casualtiesbelow] def setBloodOxygen(
      vitals: VitalsComponentImpl,
      value: Double
  ): Boolean = {
    val previous = vitals.circulation.bloodOxygen
    vitals.setBloodOxygen(value)
    vitals.circulation.bloodOxygen != previous
  }

  private[casualtiesbelow] def setBloodVolume(
      vitals: VitalsComponentImpl,
      value: Double
  ): Boolean = {
    val previous = vitals.circulation.bloodVolume
    vitals.setBloodVolume(value)
    vitals.circulation.bloodVolume != previous
  }

  private[casualtiesbelow] def setSepsis(
      vitals: VitalsComponentImpl,
      value: Double
  ): Boolean = {
    val previous = vitals.infection.sepsis
    vitals.setSepsis(value)
    vitals.infection.sepsis != previous
  }

  private[casualtiesbelow] def setDiscomfort(
      vitals: VitalsComponentImpl,
      value: Double
  ): Boolean = {
    val previous = vitals.discomfort
    vitals.setDiscomfort(value)
    vitals.discomfort != previous
  }

  private[casualtiesbelow] def applyConsciousnessState(
      vitals: VitalsComponentImpl,
      consciousness: Double,
      unconscious: Boolean
  ): Unit = vitals.applyConsciousnessState(consciousness, unconscious)

  private[casualtiesbelow] def applyPainShockState(
      vitals: VitalsComponentImpl,
      load: Double,
      stage: PainShockStage
  ): Unit = vitals.applyPainShockState(load, stage)

  private[casualtiesbelow] def adrenalineGraceTicks(vitals: VitalsComponentImpl): Int =
    vitals.adrenalineGraceTicks

  private[casualtiesbelow] def applyAdrenalineState(
      vitals: VitalsComponentImpl,
      amount: Double,
      graceTicks: Int
  ): Unit = vitals.applyAdrenalineState(amount, graceTicks)

  private[casualtiesbelow] def hypoxiaExposureTicks(vitals: VitalsComponentImpl): Int =
    vitals.hypoxiaExposureTicks

  private[casualtiesbelow] def applyHypoxiaExposureTicks(
      vitals: VitalsComponentImpl,
      ticks: Int
  ): Unit = vitals.applyHypoxiaExposureTicks(ticks)

  private[casualtiesbelow] def totemHemostasisTicks(vitals: VitalsComponentImpl): Int =
    vitals.totemHemostasisTicks

  private[casualtiesbelow] def applyTotemHemostasisTicks(
      vitals: VitalsComponentImpl,
      ticks: Int
  ): Unit = vitals.applyTotemHemostasisTicks(ticks)

  private[casualtiesbelow] def syncNow(player: Player): Unit =
    CasualtiesBelowComponents.Vitals.sync(player)

}
