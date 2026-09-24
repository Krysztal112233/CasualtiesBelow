package dev.krysztal.casualtiesbelow.physiology.opioid

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.internal.Consts

/** Derived withdrawal state and its cross-system multipliers. No withdrawal flag is persisted. */
object OpioidWithdrawal {

  def isActive(vitals: VitalsComponent): Boolean = {
    isActive(vitals.opioidLevel, vitals.opioidDependence)
  }

  private[opioid] def isActive(
      level: Double,
      dependence: Double,
      dependenceThreshold: Double = Consts.Opioid.WithdrawalDependenceThreshold,
      levelPerDependence: Double = Consts.Opioid.WithdrawalLevelPerDependence
  ): Boolean = {
    dependence > dependenceThreshold && level < dependence * levelPerDependence
  }

  def painGrantMultiplier(vitals: VitalsComponent): Double = {
    if (isActive(vitals)) Consts.Opioid.WithdrawalPainMultiplier else 1.0
  }
}
