package dev.krysztal.casualtiesbelow.opioid

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

/** Server-tick evolution of the hidden acute opioid level and synced long-term dependence. */
object OpioidProgression {

  /** Advances both opioid axes and withdrawal discomfort. Returns whether client-visible state
    * changed; level-only decay deliberately does not request synchronization.
    */
  def tick(vitals: VitalsComponentImpl): Boolean = {
    val previousDependence = vitals.opioidDependence
    val next = nextState(vitals.opioidLevel, previousDependence)
    VitalsMutations.setOpioidLevel(vitals, next.level)
    VitalsMutations.setOpioidDependence(vitals, next.dependence)

    val discomfortChanged = tickWithdrawalDiscomfort(vitals)
    next.dependence != previousDependence || discomfortChanged
  }

  private def tickWithdrawalDiscomfort(vitals: VitalsComponentImpl): Boolean = {
    if (!OpioidWithdrawal.isActive(vitals)) return false

    val target = CasualtiesBelowConfig.OpioidWithdrawalDiscomfortTarget.get()
    if (vitals.discomfort >= target) return false

    VitalsMutations.setDiscomfort(
      vitals,
      (vitals.discomfort + CasualtiesBelowConfig.OpioidWithdrawalDiscomfortPerTick.get())
        .min(target)
    )
  }

  private[opioid] def nextState(level: Double, dependence: Double): OpioidState = {
    nextState(
      level,
      dependence,
      CasualtiesBelowConfig.OpioidLevelDecayPerTick.get(),
      CasualtiesBelowConfig.OpioidDependenceExposurePerLevelPerTick.get(),
      CasualtiesBelowConfig.OpioidDependenceDecayPerTick.get()
    )
  }

  private[opioid] def nextState(
      level: Double,
      dependence: Double,
      levelDecayPerTick: Double,
      exposurePerLevelPerTick: Double,
      dependenceDecayPerTick: Double
  ): OpioidState = {
    val boundedLevel = normalize(level, VitalsComponent.MaxOpioidLevel)
    val boundedDependence = normalize(dependence, VitalsComponent.MaxOpioidDependence)
    val exposure = boundedLevel * exposurePerLevelPerTick.max(0.0)
    val dependenceDelta = if (exposure > 0.0) exposure else -dependenceDecayPerTick.max(0.0)

    OpioidState(
      (boundedLevel - levelDecayPerTick.max(0.0)).max(0.0),
      (boundedDependence + dependenceDelta).max(0.0).min(VitalsComponent.MaxOpioidDependence)
    )
  }

  private def normalize(value: Double, maximum: Double): Double = {
    if (value == Double.PositiveInfinity) maximum
    else if (value.isFinite) value.max(0.0).min(maximum)
    else 0.0
  }
}

private[opioid] final case class OpioidState(level: Double, dependence: Double)
