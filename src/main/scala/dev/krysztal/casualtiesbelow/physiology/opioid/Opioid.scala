package dev.krysztal.casualtiesbelow.physiology.opioid

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extension.Prelude.*

/** Server-tick evolution of the hidden acute opioid level and synced long-term dependence. */
object Opioid {

  /** Advances both opioid axes and withdrawal discomfort. Returns whether client-visible state
    * changed; level-only decay deliberately does not request synchronization.
    */
  def tick(vitals: VitalsComponentImpl): Boolean = {
    val previousDependence = vitals.opioidDependence
    val next = nextState(vitals.opioidLevel, previousDependence)
    VitalsMutations.applyOpioidState(vitals, next)

    val discomfortChanged = tickWithdrawalDiscomfort(vitals)
    next.dependence != previousDependence || discomfortChanged
  }

  private def tickWithdrawalDiscomfort(vitals: VitalsComponentImpl): Boolean = {
    if (!OpioidWithdrawal.isActive(vitals)) return false

    val next = nextWithdrawalDiscomfort(vitals.discomfort)
    if (next == vitals.discomfort) return false

    VitalsMutations.setDiscomfort(vitals, next)
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
