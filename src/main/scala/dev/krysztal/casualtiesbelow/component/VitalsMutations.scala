package dev.krysztal.casualtiesbelow.component

import net.minecraft.world.entity.player.Player

import dev.krysztal.casualtiesbelow.adrenaline.AdrenalineState
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.ConsciousnessSnapshot
import dev.krysztal.casualtiesbelow.api.body.ShockSnapshot

/** Internal write authority for the mutable storage behind the public read-only vitals view. */
object VitalsMutations {
  private[casualtiesbelow] def setImmuneHealth(
      vitals: VitalsComponentImpl,
      value: Double
  ): Boolean = {
    val previous = vitals.infection
    vitals.setImmuneHealth(value)
    vitals.infection != previous
  }

  private[casualtiesbelow] def setBloodOxygen(
      vitals: VitalsComponentImpl,
      value: Double
  ): Boolean = {
    val previous = vitals.circulation
    vitals.setBloodOxygen(value)
    vitals.circulation != previous
  }

  private[casualtiesbelow] def setBloodVolume(
      vitals: VitalsComponentImpl,
      value: Double
  ): Boolean = {
    val previous = vitals.circulation
    vitals.setBloodVolume(value)
    vitals.circulation != previous
  }

  private[casualtiesbelow] def setSepsis(
      vitals: VitalsComponentImpl,
      value: Double
  ): Boolean = {
    val previous = vitals.infection
    vitals.setSepsis(value)
    vitals.infection != previous
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
      state: ConsciousnessSnapshot
  ): Unit = vitals.applyConsciousnessState(state)

  private[casualtiesbelow] def applyShockState(
      vitals: VitalsComponentImpl,
      state: ShockSnapshot
  ): Unit = vitals.applyShockState(state)

  private[casualtiesbelow] def adrenalineReserve(vitals: VitalsComponentImpl): AdrenalineState =
    vitals.adrenalineReserve

  private[casualtiesbelow] def adrenalineGraceTicks(vitals: VitalsComponentImpl): Int =
    vitals.adrenalineGraceTicks

  private[casualtiesbelow] def applyAdrenalineState(
      vitals: VitalsComponentImpl,
      state: AdrenalineState
  ): Unit = vitals.applyAdrenalineState(state)

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
