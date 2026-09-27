package dev.krysztal.casualtiesbelow.physiology.wetness

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent

/** Pure formula steps of the wetness recurrence, extracted from [[Wetness]] so every stage can be
  * unit-tested without a running game: the progression reads world state, then delegates the math
  * here.
  */
object WetnessCalc {

  /** Sweat rate (axis points per second) from core temperature: linear at `slopePerDegree` above
    * the threshold, zero at or below it. Exertion enters only indirectly, through exercise heat
    * raising the core.
    */
  def sweatRatePerSecond(
      coreTemperature: Double,
      threshold: Double,
      slopePerDegree: Double
  ): Double =
    ((coreTemperature - threshold) * slopePerDegree).max(0.0)

  /** Wetness accumulator step, clamped to the 0..100 axis. */
  def nextWetness(wetness: Double, delta: Double): Double =
    (wetness + delta).max(0.0).min(VitalsComponent.MaxWetness)
}
