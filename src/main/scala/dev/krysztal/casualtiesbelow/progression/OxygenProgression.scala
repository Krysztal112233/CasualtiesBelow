package dev.krysztal.casualtiesbelow.progression

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.gamerules.GameRules

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.blood.BloodVolume
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.opioid.OpioidEffects

/** Couples vanilla breath, in-wall suffocation, and custom blood volume into blood oxygen.
  *
  * Blood volume determines instantaneous carrying capacity. Exhausted vanilla air blocks breathing
  * only while `drowningDamage` is enabled; this keeps Respiration, Water Breathing, bubble columns,
  * air recovery, and the gamerule upstream of custom physiology. Being in a wall is an independent
  * breathing block. Simultaneous blocks use the stronger configured deprivation rate rather than
  * stacking. Vanilla drowning/in-wall damage pulses are not translated into another loss.
  *
  * This object does not write consciousness. [[ConsciousnessProgression]] interprets the stored
  * oxygen as a pressure, while [[HypoxiaProgression]] owns hidden terminal exposure metadata.
  */
object OxygenProgression {

  /** Advances blood oxygen by one server tick and reports both mutation and breathing ownership for
    * downstream terminal-hypoxia progression.
    */
  private[progression] def tick(
      player: ServerPlayer,
      vitals: VitalsComponentImpl
  ): OxygenProgressionResult = {
    val inWall = player.isInWall
    val exhaustedAir =
      player.level().getGameRules.get(GameRules.DROWNING_DAMAGE).booleanValue &&
        player.getMaxAirSupply > 0 && player.getAirSupply <= 0
    val deprivationRate =
      if (inWall && exhaustedAir) {
        CasualtiesBelowConfig.InWallBloodOxygenDepletionPerTick
          .get()
          .doubleValue
          .max(CasualtiesBelowConfig.BloodOxygenDepletionPerTick.get().doubleValue)
      } else if (inWall) {
        CasualtiesBelowConfig.InWallBloodOxygenDepletionPerTick.get().doubleValue
      } else if (exhaustedAir) {
        CasualtiesBelowConfig.BloodOxygenDepletionPerTick.get().doubleValue
      } else {
        0.0
      }

    val breathingBlocked = inWall || exhaustedAir
    val boundedDeprivationRate = finiteNonNegative(deprivationRate)
    val respiratoryEfficiency =
      OpioidEffects.respiratoryEfficiency(vitals.opioidLevel, vitals.opioidDependence)
    val opioidRespiratoryFailure = OpioidEffects.causesRespiratoryFailure(respiratoryEfficiency)
    val previousOxygen = vitals.circulation.bloodOxygen
    VitalsMutations.setBloodOxygen(
      vitals,
      nextBloodOxygen(
        vitals,
        breathingBlocked,
        boundedDeprivationRate,
        respiratoryEfficiency,
        opioidRespiratoryFailure
      )
    )
    OxygenProgressionResult(
      changed = !same(previousOxygen, vitals.circulation.bloodOxygen),
      breathingBlocked = breathingBlocked,
      respirationFailed = breathingBlocked || opioidRespiratoryFailure,
      deprivationRate = boundedDeprivationRate
    )
  }

  private def nextBloodOxygen(
      vitals: VitalsComponent,
      breathingBlocked: Boolean,
      deprivationRate: Double,
      respiratoryEfficiency: Double,
      opioidRespiratoryFailure: Boolean
  ): Double = {
    nextBloodOxygen(
      vitals.circulation.bloodOxygen,
      BloodVolume.oxygenCarryingCapacity(vitals),
      breathingBlocked,
      deprivationRate,
      CasualtiesBelowConfig.BloodOxygenRecoveryPerTick.get(),
      respiratoryEfficiency,
      opioidRespiratoryFailure,
      if (opioidRespiratoryFailure) {
        CasualtiesBelowConfig.OpioidRespiratoryFailureOxygenDrainPerTick.get()
      } else {
        0.0
      }
    )
  }

  private[progression] def nextBloodOxygen(
      bloodOxygen: Double,
      carryingCapacity: Double,
      breathingBlocked: Boolean,
      deprivationRate: Double,
      recoveryRate: Double
  ): Double = {
    nextBloodOxygen(
      bloodOxygen,
      carryingCapacity,
      breathingBlocked,
      deprivationRate,
      recoveryRate,
      respiratoryEfficiency = 1.0,
      respirationFailed = false,
      respiratoryFailureDrain = 0.0
    )
  }

  private[progression] def nextBloodOxygen(
      bloodOxygen: Double,
      carryingCapacity: Double,
      breathingBlocked: Boolean,
      deprivationRate: Double,
      recoveryRate: Double,
      respiratoryEfficiency: Double,
      respirationFailed: Boolean,
      respiratoryFailureDrain: Double
  ): Double = {
    val capacity = normalizedOxygen(carryingCapacity, VitalsComponent.MaxBloodOxygen)
    val current = normalizedOxygen(bloodOxygen, capacity)

    // Oxygen above a newly reduced blood-volume capacity is lost immediately. Vanilla breathing
    // blocks retain their deprivation semantics; otherwise respiratory efficiency scales recovery,
    // with severe opioid failure replacing recovery with a fixed net drain.
    if (breathingBlocked) {
      (current - finiteNonNegative(deprivationRate)).max(0.0)
    } else if (respirationFailed) {
      (current - finiteNonNegative(respiratoryFailureDrain)).max(0.0)
    } else {
      val efficiency = finiteNonNegative(respiratoryEfficiency).min(1.0)
      (current + finiteNonNegative(recoveryRate) * efficiency).min(capacity)
    }
  }

  private def normalizedOxygen(value: Double, capacity: Double): Double = {
    if (value == Double.PositiveInfinity) capacity
    else if (value.isFinite) value.max(0.0).min(capacity)
    else 0.0
  }

  private def finiteNonNegative(value: Double): Double = {
    if (value == Double.PositiveInfinity) Double.MaxValue
    else if (value.isFinite) value.max(0.0)
    else 0.0
  }

  private def same(left: Double, right: Double): Boolean = {
    java.lang.Double.doubleToLongBits(left) == java.lang.Double.doubleToLongBits(right)
  }
}

private[progression] final case class OxygenProgressionResult(
    changed: Boolean,
    breathingBlocked: Boolean,
    respirationFailed: Boolean,
    deprivationRate: Double
)
