package dev.krysztal.casualtiesbelow.physiology.opioid

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*

/** Server-tick evolution of the hidden acute opioid level and synced long-term dependence. */
object Opioid {

  /** Advances both opioid axes and withdrawal discomfort. */
  def tick(vitals: VitalsComponentImpl): Unit = {
    vitals.applyOpioidState(nextState(vitals.opioidLevel, vitals.opioidDependence))
    tickWithdrawalDiscomfort(vitals)
  }

  private def tickWithdrawalDiscomfort(vitals: VitalsComponentImpl): Unit = {
    if (OpioidWithdrawal.isActive(vitals)) {
      vitals.setDiscomfort(nextWithdrawalDiscomfort(vitals.discomfort))
    }
  }

  private[casualtiesbelow] def nextWithdrawalDiscomfort(
      discomfort: Double,
      gainPerTick: Double = Consts.Opioid.WithdrawalDiscomfortPerTick,
      target: Double = Consts.Opioid.WithdrawalDiscomfortTarget
  ): Double = {
    val boundedTarget = target.max(0.0)
    if (discomfort >= boundedTarget) discomfort
    else (discomfort + gainPerTick.max(0.0)).min(boundedTarget)
  }

  private[opioid] def nextState(
      level: Double,
      dependence: Double,
      levelDecayPerTick: Double = Consts.Opioid.LevelDecayPerTick,
      exposurePerLevelPerTick: Double = Consts.Opioid.DependenceExposurePerLevelPerTick,
      dependenceDecayPerTick: Double = Consts.Opioid.DependenceDecayPerTick
  ): OpioidState = {
    val boundedLevel = level.bounded(VitalsComponent.MaxOpioidLevel)
    val boundedDependence = dependence.bounded(VitalsComponent.MaxOpioidDependence)
    val exposure = boundedLevel * exposurePerLevelPerTick.max(0.0)
    val dependenceDelta = exposure - dependenceDecayPerTick.max(0.0)

    OpioidState(
      (boundedLevel - levelDecayPerTick.max(0.0)).max(0.0),
      (boundedDependence + dependenceDelta).max(0.0).min(VitalsComponent.MaxOpioidDependence)
    )
  }

}
