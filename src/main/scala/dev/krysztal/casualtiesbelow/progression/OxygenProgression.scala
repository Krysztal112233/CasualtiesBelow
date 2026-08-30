package dev.krysztal.casualtiesbelow.progression

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.gamerules.GameRules

import dev.krysztal.casualtiesbelow.api.body.VitalsComponent
import dev.krysztal.casualtiesbelow.blood.BloodVolume
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

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
    val previousOxygen = vitals.bloodOxygen
    VitalsMutations.setBloodOxygen(
      vitals,
      nextBloodOxygen(vitals, breathingBlocked, boundedDeprivationRate)
    )
    OxygenProgressionResult(
      changed = !same(previousOxygen, vitals.bloodOxygen),
      breathingBlocked = breathingBlocked,
      deprivationRate = boundedDeprivationRate
    )
  }

  private def nextBloodOxygen(
      vitals: VitalsComponent,
      breathingBlocked: Boolean,
      deprivationRate: Double
  ): Double = {
    val capacity = BloodVolume.oxygenCarryingCapacity(vitals)
    val current = normalizedOxygen(vitals.bloodOxygen, capacity)

    // Oxygen above a newly reduced blood-volume capacity is lost immediately. Active deprivation
    // and recovery remain gradual at their configured rates.
    if (breathingBlocked) {
      (current - deprivationRate).max(0.0)
    } else {
      (current + finiteNonNegative(CasualtiesBelowConfig.BloodOxygenRecoveryPerTick.get()))
        .min(capacity)
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
    deprivationRate: Double
)
