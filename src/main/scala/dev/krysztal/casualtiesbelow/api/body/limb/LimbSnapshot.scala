package dev.krysztal.casualtiesbelow.api.body.limb

import java.util.OptionalDouble
import java.util.OptionalInt

/** Immutable, language-neutral view of one limb's current condition. */
final case class LimbSnapshot(
    muscleHealth: Double,
    skinIntegrity: Double,
    fractureRecoveryTicks: OptionalInt,
    infectionProgress: OptionalDouble,
    dislocated: Boolean,
    externalBleedingRate: Double,
    pain: Double
) {
  def fractured: Boolean = fractureRecoveryTicks.isPresent
  def infected: Boolean = infectionProgress.isPresent
}

object LimbSnapshot {
  val MaxValue: Double = 100.0
}
