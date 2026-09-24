package dev.krysztal.casualtiesbelow.physiology.temperature

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent

/** Pure formula steps of the body-temperature recurrence, extracted from [[Temperature]] so every
  * stage can be unit-tested without a running game: the progression reads config and world state,
  * then delegates the math here.
  *
  * Fixed EvalEx sources live in [[dev.krysztal.casualtiesbelow.internal.Consts.Temperature]]; tests
  * compile those sources directly without a loaded config file.
  */
object TemperatureCalc {

  /** Fraction of a full sprint's sweat rate at a given exertion rate (exhaustion per second):
    * scales linearly up to sprint reference exertion and saturates beyond it. The reference 0.56/s
    * matches vanilla sprinting and is the same anchor the exercise-heat listener uses.
    */
  def sweatRateFraction(exhaustionPerSecond: Double, sprintExhaustionPerSecond: Double): Double =
    (exhaustionPerSecond / sprintExhaustionPerSecond).max(0.0).min(1.0)

  /** Liquid water never goes below freezing: immersion floors the apparent temperature at 0°C. */
  def apparentTemperature(mapped: Double, immersed: Boolean): Double =
    if (immersed) mapped.max(0.0) else mapped

  /** Cold-side deviation outside the penalty band: how many °C the body temperature sits below its
    * lower bound, or zero inside/above the band.
    */
  def coldDeviation(bodyTemperature: Double, bandLow: Double): Double =
    (bandLow - bodyTemperature).max(0.0)

  /** Hot-side deviation outside the penalty band: how many °C the body temperature sits above its
    * upper bound, or zero inside/below the band.
    */
  def hotDeviation(bodyTemperature: Double, bandHigh: Double): Double =
    (bodyTemperature - bandHigh).max(0.0)

  /** Per-tick immune drain from one side's band deviation: °C × coefficient/min → /tick (1200 ticks
    * per minute).
    */
  def immuneDrainPerTick(
      deviation: Double,
      drainPerDegreePerMinute: Double
  ): Double =
    deviation * drainPerDegreePerMinute / 1200.0

  /** Armor insulation shrinks the equilibrium's deviation from normal body temperature, gated to
    * the cold side (t <= 37): in heat, clothing neither insulates nor refrigerates — the heat-side
    * thermal role of clothing belongs to the dissipation-block coefficient alone.
    */
  def effectiveEquilibrium(equilibrium: Double, insulation: Double): Double = {
    val normal = VitalsComponent.NormalBodyTemperature
    val coldWeight = if (equilibrium > normal) 0.0 else 1.0
    normal + (equilibrium - normal) * (1.0 - insulation * coldWeight)
  }

  /** Approach rate (1/s) from the air time constant, sped up while immersed. */
  def approachRatePerSecond(
      tauAirMinutes: Double,
      immersed: Boolean,
      immersionRateMultiplier: Double
  ): Double =
    (if (immersed) immersionRateMultiplier else 1.0) / (tauAirMinutes * 60.0)

  /** Combines the per-minute contribution totals into per-second production; the dissipative
    * channel pays the armor's surviving dissipation block while the direct channel bypasses it.
    */
  def productionPerSecond(
      directPerMinute: Double,
      dissipativePerMinute: Double,
      dissipationBlock: Double
  ): Double =
    (directPerMinute - dissipativePerMinute * (1.0 - dissipationBlock)) / 60.0

  /** One step of the exponential approach plus production terms. */
  def nextCoreTemperature(
      core: Double,
      effectiveEquilibrium: Double,
      ratePerSecond: Double,
      productionPerSecond: Double,
      dtSeconds: Double
  ): Double =
    core + dtSeconds * (ratePerSecond * (effectiveEquilibrium - core) + productionPerSecond)

  /** Wetness accumulator step, clamped to the 0..1 axis. */
  def nextWetness(wetness: Double, delta: Double): Double =
    (wetness + delta).max(0.0).min(VitalsComponent.MaxWetness)
}
