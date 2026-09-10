package dev.krysztal.casualtiesbelow.opioid

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

/** Derived withdrawal state and its cross-system multipliers. No withdrawal flag is persisted. */
object OpioidWithdrawal {

  def isActive(vitals: VitalsComponent): Boolean = {
    isActive(vitals.opioidLevel, vitals.opioidDependence)
  }

  def isActive(level: Double, dependence: Double): Boolean = {
    isActive(
      level,
      dependence,
      CasualtiesBelowConfig.OpioidWithdrawalDependenceThreshold.get(),
      CasualtiesBelowConfig.OpioidWithdrawalLevelPerDependence.get()
    )
  }

  private[opioid] def isActive(
      level: Double,
      dependence: Double,
      dependenceThreshold: Double,
      levelPerDependence: Double
  ): Boolean = {
    dependence > dependenceThreshold && level < dependence * levelPerDependence
  }

  def painGrantMultiplier(vitals: VitalsComponent): Double = {
    if (isActive(vitals)) CasualtiesBelowConfig.OpioidWithdrawalPainMultiplier.get() else 1.0
  }
}
