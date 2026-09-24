package dev.krysztal.casualtiesbelow.physiology.opioid

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.internal.Consts

/** Derived withdrawal state and its cross-system multipliers. No withdrawal flag is persisted. */
object OpioidWithdrawal {

  def isActive(vitals: VitalsComponent): Boolean = {
    isActive(vitals.opioidLevel, vitals.opioidDependence)
  }

  def isActive(level: Double, dependence: Double): Boolean = {
    isActive(
      level,
      dependence,
      Consts.Opioid.WithdrawalDependenceThreshold,
      Consts.Opioid.WithdrawalLevelPerDependence
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
    if (isActive(vitals)) Consts.Opioid.WithdrawalPainMultiplier else 1.0
  }
}
