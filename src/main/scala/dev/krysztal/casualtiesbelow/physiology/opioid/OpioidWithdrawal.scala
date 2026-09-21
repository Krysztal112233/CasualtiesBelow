package dev.krysztal.casualtiesbelow.physiology.opioid

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.extension.ConfigValueExtensions.*

/** Derived withdrawal state and its cross-system multipliers. No withdrawal flag is persisted. */
object OpioidWithdrawal {

  def isActive(vitals: VitalsComponent): Boolean = {
    isActive(vitals.opioidLevel, vitals.opioidDependence)
  }

  def isActive(level: Double, dependence: Double): Boolean = {
    isActive(
      level,
      dependence,
      CasualtiesBelowConfig.OpioidWithdrawalDependenceThreshold.value,
      CasualtiesBelowConfig.OpioidWithdrawalLevelPerDependence.value
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
    if (isActive(vitals)) CasualtiesBelowConfig.OpioidWithdrawalPainMultiplier.value else 1.0
  }
}
