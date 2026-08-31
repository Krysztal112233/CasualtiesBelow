package dev.krysztal.casualtiesbelow.component

import java.util.OptionalDouble
import java.util.OptionalInt

import dev.krysztal.casualtiesbelow.api.body.limb.LimbSnapshot

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
    muscleHealth = bounded(state.muscleHealth, LimbSnapshot.MaxValue),
    skinIntegrity = bounded(state.skinIntegrity, LimbSnapshot.MaxValue),
    fractureRecoveryTicks = state.fractureRecoveryTicks.map(_.max(0)),
    infectionProgress = state.infectionProgress.map(value => bounded(value, LimbSnapshot.MaxValue)),
    dislocated = state.dislocated,
    externalBleedingRate = nonNegative(state.externalBleedingRate),
    pain = bounded(state.pain, LimbSnapshot.MaxValue)
  )

  private def bounded(value: Double, maximum: Double): Double = {
    if (value == Double.PositiveInfinity) maximum
    else if (value.isFinite) value.max(0.0).min(maximum)
    else 0.0
  }

  private def nonNegative(value: Double): Double = {
    if (value == Double.PositiveInfinity) Double.MaxValue
    else if (value.isFinite) value.max(0.0)
    else 0.0
  }
}
