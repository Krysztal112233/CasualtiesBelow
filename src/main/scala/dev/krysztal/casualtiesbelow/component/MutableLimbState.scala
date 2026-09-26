package dev.krysztal.casualtiesbelow.component

import java.util.OptionalDouble
import java.util.OptionalInt

import dev.krysztal.casualtiesbelow.api.body.limb.LimbSnapshot
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*

/** Mutable storage model used only while the server applies or advances physiology. */
private[casualtiesbelow] final case class MutableLimbState(
    var muscleHealth: Double = LimbSnapshot.MaxValue,
    var skinIntegrity: Double = LimbSnapshot.MaxValue,
    var fractureRecoveryTicks: Option[Int] = None,
    var infectionProgress: Option[Double] = None,
    var dislocated: Boolean = false,
    var externalBleedingRate: Double = 0.0,
    var pain: Double = 0.0
) {
  def snapshot: LimbSnapshot = new LimbSnapshot(
    muscleHealth,
    skinIntegrity,
    fractureRecoveryTicks.fold(OptionalInt.empty())(OptionalInt.of),
    infectionProgress.fold(OptionalDouble.empty())(OptionalDouble.of),
    dislocated,
    externalBleedingRate,
    pain
  )
}

private[casualtiesbelow] object MutableLimbState {
  val MaxValue: Double = LimbSnapshot.MaxValue

  def from(snapshot: LimbSnapshot): MutableLimbState = MutableLimbState(
    muscleHealth = snapshot.muscleHealth,
    skinIntegrity = snapshot.skinIntegrity,
    fractureRecoveryTicks =
      if (snapshot.fractureRecoveryTicks.isPresent)
        Some(snapshot.fractureRecoveryTicks.getAsInt)
      else None,
    infectionProgress =
      if (snapshot.infectionProgress.isPresent) Some(snapshot.infectionProgress.getAsDouble)
      else None,
    dislocated = snapshot.dislocated,
    externalBleedingRate = snapshot.externalBleedingRate,
    pain = snapshot.pain
  )

  def normalize(state: MutableLimbState): MutableLimbState = MutableLimbState(
    muscleHealth = state.muscleHealth.bounded(LimbSnapshot.MaxValue),
    skinIntegrity = state.skinIntegrity.bounded(LimbSnapshot.MaxValue),
    fractureRecoveryTicks = state.fractureRecoveryTicks.map(_.max(0)),
    infectionProgress = state.infectionProgress.map(_.bounded(LimbSnapshot.MaxValue)),
    dislocated = state.dislocated,
    externalBleedingRate = state.externalBleedingRate.nonNegative,
    pain = state.pain.bounded(LimbSnapshot.MaxValue)
  )
}
