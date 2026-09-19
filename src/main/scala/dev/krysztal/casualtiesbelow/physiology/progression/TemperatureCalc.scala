package dev.krysztal.casualtiesbelow.physiology.progression

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent

/** Pure formula steps of the body-temperature recurrence, extracted from [[TemperatureProgression]]
  * so every stage can be unit-tested without a running game: the progression reads config and world
  * state, then delegates the math here.
  *
  * The default EvalEx sources of the configurable curves also live here (not in the config file) so
  * unit tests can compile them directly — config values cannot be read without a loaded config
  * file, and the GameTest world's biome is not controllable, so curve behavior is verified here
  * rather than in-game.
  */
object TemperatureCalc {

  /** Default source of `CasualtiesBelowConfig.BiomeMappingFormula`: anchors the vanilla rain/snow
    * line 0.15 to 0°C and desert 2.0 to 40°C.
    */
  val BiomeMappingFormulaDefault = "(t - 0.15) * 40 / 1.85"

  /** Default source of `CasualtiesBelowConfig.ComfortBandFormula`: inside the band the equilibrium
    * is normal body temperature, outside it deviates by the slope.
    */
  val ComfortBandFormulaDefault =
    "if(t < low, 37 + (t - low) * slope, if(t > high, 37 + (t - high) * slope, 37))"

  /** Default source of `CasualtiesBelowConfig.DryingCurveFormula`: wetness lost per second from the
    * drying temperature (this EvalEx configuration has no exp(), so e's power is numeric).
    */
  val DryingCurveFormulaDefault = "0.0014 * 2.718281828459045^(0.06 * t)"

  /** Default source of `CasualtiesBelowConfig.WetnessCollapseFormula`: fraction of armor thermal
    * coefficients surviving at a given wetness.
    */
  val WetnessCollapseFormulaDefault = "1 - 0.85 * wetness"

  /** Liquid water never goes below freezing: immersion floors the apparent temperature at 0°C. */
  def apparentTemperature(mapped: Double, immersed: Boolean): Double =
    if (immersed) mapped.max(0.0) else mapped

  /** Armor insulation shrinks the equilibrium's deviation from normal body temperature. */
  def effectiveEquilibrium(equilibrium: Double, insulation: Double): Double = {
    val normal = VitalsComponent.NormalBodyTemperature
    normal + (equilibrium - normal) * (1.0 - insulation)
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
