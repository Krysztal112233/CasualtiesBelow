package dev.krysztal.casualtiesbelow.physiology.wetness

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent

/** Pure formula steps of the wetness recurrence, extracted from [[Wetness]] so every stage can be
  * unit-tested without a running game: the progression reads world state, then delegates the math
  * here.
  */
object WetnessCalc {

  /** Fraction of a full sprint's sweat rate at a given exertion rate (exhaustion per second):
    * scales linearly up to sprint reference exertion and saturates beyond it. The reference 0.56/s
    * matches vanilla sprinting and is the same anchor the exercise-heat listener uses.
    */
  def sweatRateFraction(exhaustionPerSecond: Double, sprintExhaustionPerSecond: Double): Double =
    (exhaustionPerSecond / sprintExhaustionPerSecond).max(0.0).min(1.0)

  /** Wetness accumulator step, clamped to the 0..1 axis. */
  def nextWetness(wetness: Double, delta: Double): Double =
    (wetness + delta).max(0.0).min(VitalsComponent.MaxWetness)
}
