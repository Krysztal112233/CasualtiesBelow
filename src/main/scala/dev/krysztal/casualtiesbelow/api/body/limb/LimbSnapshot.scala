package dev.krysztal.casualtiesbelow.api.body.limb

import java.util.OptionalDouble
import java.util.OptionalInt

/** Immutable, language-neutral view of one limb's current condition. */
final class LimbSnapshot(
    val muscleHealth: Double,
    val skinIntegrity: Double,
    val fractureRecoveryTicks: OptionalInt,
    val infectionProgress: OptionalDouble,
    val dislocated: Boolean,
    val externalBleedingRate: Double,
    val pain: Double
) {
  def fractured: Boolean = fractureRecoveryTicks.isPresent
  def infected: Boolean = infectionProgress.isPresent
}

object LimbSnapshot {
  val MaxValue: Double = 100.0
}
