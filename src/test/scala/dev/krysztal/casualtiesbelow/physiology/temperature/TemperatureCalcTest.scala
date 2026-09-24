package dev.krysztal.casualtiesbelow.physiology.temperature

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap

import dev.krysztal.casualtiesbelow.config.FormulaConfigValue
import dev.krysztal.casualtiesbelow.internal.Consts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Unit coverage for the pure formula steps ([[TemperatureCalc]]) and the default EvalEx curves.
  *
  * The default formula sources are compiled straight from [[TemperatureCalc]]'s constants because
  * config values cannot be read without a loaded config file; evaluating through
  * [[FormulaConfigValue.compile]] still exercises the real EvalEx pipeline the game uses.
  */
final class TemperatureCalcTest {

  SharedConstants.tryDetectVersion()
  Bootstrap.bootStrap()

  // ---- Default curve sources -------------------------------------------------

  @Test
  def biomeMappingAnchorsRainSnowLineAndDesert(): Unit = {
    assertEquals(0.0, biomeMapping(0.15), 1.0e-9, "rain/snow line must map to 0°C")
    assertEquals(40.0, biomeMapping(2.0), 1.0e-9, "desert must map to 40°C")
    assertEquals(14.054, biomeMapping(0.8), 0.001, "plains-like biomes sit near 14°C")
  }

  @Test
  def comfortBandKeepsNormalTemperatureInside(): Unit = {
    // NOTE: the 10/28/0.3 band mirrors CasualtiesBelowConfig defaults; keep in sync when
    // calibrating (phase 5).
    assertEquals(37.0, comfortBand(14.0), 1.0e-9)
    assertEquals(37.0, comfortBand(10.0), 1.0e-9, "lower bound is inside the band")
    assertEquals(37.0, comfortBand(28.0), 1.0e-9, "upper bound is inside the band")
  }

  @Test
  def comfortBandDeviatesBySlopeOutside(): Unit = {
    assertEquals(29.8, comfortBand(-14.0), 1.0e-9, "snowy taiga example from the design doc")
    assertEquals(40.6, comfortBand(40.0), 1.0e-9, "desert example from the design doc")
    assertEquals(33.04, comfortBand(-3.2), 0.001, "snowy plains example from the design doc")
  }

  @Test
  def comfortBandIsContinuousAtBothBounds(): Unit = {
    val eps = 1.0e-6
    assertEquals(comfortBand(10.0 - eps), comfortBand(10.0 + eps), 1.0e-3)
    assertEquals(comfortBand(28.0 - eps), comfortBand(28.0 + eps), 1.0e-3)
  }

  @Test
  def wetnessCollapseLeavesFifteenPercentWhenSoaked(): Unit = {
    assertEquals(1.0, collapse(0.0), 1.0e-9, "dry armor keeps full coefficients")
    assertEquals(0.15, collapse(1.0), 1.0e-9, "soaked armor collapses to 15%")
    assertEquals(0.09, 0.6 * collapse(1.0), 1.0e-9, "soaked leather insulation: 0.6 -> 0.09")
  }

  @Test
  def dryingCurveNearlyStopsInSnowAndFlashDriesOnFire(): Unit = {
    val snowyPlains = drying(-3.2)
    assertTrue(snowyPlains < 0.002, s"frozen clothing must barely dry, got $snowyPlains")
    val onFire = drying(17.0 + 60.0)
    assertTrue(onFire > 0.05, s"on fire must dry wetness in seconds, got $onFire")
    assertTrue(
      drying(40.0) > drying(17.0) && drying(17.0) > drying(-3.2),
      "drying rate must grow with temperature"
    )
  }

  // ---- Pure steps --------------------------------------------------------------

  @Test
  def immersionFloorsApparentTemperatureAtFreezing(): Unit = {
    assertEquals(0.0, TemperatureCalc.apparentTemperature(-5.0, immersed = true), 1.0e-9)
    assertEquals(-5.0, TemperatureCalc.apparentTemperature(-5.0, immersed = false), 1.0e-9)
    assertEquals(14.0, TemperatureCalc.apparentTemperature(14.0, immersed = true), 1.0e-9)
  }

  @Test
  def insulationAppliesColdSideOnly(): Unit = {
    assertEquals(
      33.0,
      TemperatureCalc.effectiveEquilibrium(33.0, 0.0),
      1.0e-9,
      "naked: full deviation"
    )
    assertEquals(
      35.4,
      TemperatureCalc.effectiveEquilibrium(33.0, 0.6),
      1.0e-9,
      "60% insulation keeps 40% of the cold deviation"
    )
    assertEquals(
      37.0,
      TemperatureCalc.effectiveEquilibrium(33.0, 1.0),
      1.0e-9,
      "full insulation cancels the cold environment"
    )
    assertEquals(
      40.6,
      TemperatureCalc.effectiveEquilibrium(40.6, 0.6),
      1.0e-9,
      "hot side: insulation is inert, clothing cannot refrigerate"
    )
    assertEquals(
      40.6,
      TemperatureCalc.effectiveEquilibrium(40.6, 0.0),
      1.0e-9,
      "hot side: naked and clothed converge identically"
    )
  }

  @Test
  def approachRateDerivesFromTauAndImmersionMultiplier(): Unit = {
    // NOTE: tau/multiplier literals mirror CasualtiesBelowConfig temperature defaults; keep in
    // sync when calibrating (phase 5).
    assertEquals(
      1.0 / 180.0,
      TemperatureCalc.approachRatePerSecond(3.0, immersed = false, 2.0),
      1.0e-12,
      "tau = 3 min in air"
    )
    assertEquals(
      1.0 / 90.0,
      TemperatureCalc.approachRatePerSecond(3.0, immersed = true, 2.0),
      1.0e-12,
      "immersion halves the time constant"
    )
  }

  @Test
  def productionPaysDissipationBlockButNotDirect(): Unit = {
    assertEquals(
      0.1,
      TemperatureCalc.productionPerSecond(6.0, 0.0, 0.5),
      1.0e-12,
      "direct channel bypasses armor"
    )
    assertEquals(
      -0.1,
      TemperatureCalc.productionPerSecond(0.0, 6.0, 0.0),
      1.0e-12,
      "naked dissipative channel is unmitigated"
    )
    assertEquals(
      -0.05,
      TemperatureCalc.productionPerSecond(0.0, 6.0, 0.5),
      1.0e-12,
      "dissipative channel pays the block"
    )
  }

  @Test
  def recurrenceMovesTowardsEquilibriumWithoutOvershoot(): Unit = {
    val rate = TemperatureCalc.approachRatePerSecond(3.0, immersed = false, 2.0)
    val dt = 1.0 / 20.0

    // Cold environment: the core cools, monotonically, never crossing the equilibrium.
    var core = 37.0
    (1 to 20000).foreach { _ =>
      val next = TemperatureCalc.nextCoreTemperature(core, 30.0, rate, 0.0, dt)
      assertTrue(next < core, "cold environment must cool the core")
      assertTrue(next >= 30.0, "approach must not overshoot the equilibrium")
      core = next
    }
    assertTrue(core < 30.1, s"~5.5 time constants should close the gap, got $core")

    // Hot environment: symmetric.
    core = 37.0
    (1 to 20000).foreach { _ =>
      val next = TemperatureCalc.nextCoreTemperature(core, 40.0, rate, 0.0, dt)
      assertTrue(next > core, "hot environment must warm the core")
      assertTrue(next <= 40.0, "approach must not overshoot the equilibrium")
      core = next
    }

    // At equilibrium nothing moves; production shifts it linearly.
    val stayed = TemperatureCalc.nextCoreTemperature(37.0, 37.0, rate, 0.0, dt)
    assertEquals(37.0, stayed, 1.0e-12, "in the comfort band the core must not drift")
    val produced =
      TemperatureCalc.nextCoreTemperature(37.0, 37.0, rate, 0.05, dt) - 37.0
    assertEquals(0.05 * dt, produced, 1.0e-12, "production applies directly per second")
  }

  @Test
  def wetnessStepClampsToTheAxis(): Unit = {
    assertEquals(1.0, TemperatureCalc.nextWetness(0.9, 0.2), 1.0e-9)
    assertEquals(0.0, TemperatureCalc.nextWetness(0.1, -0.2), 1.0e-9)
    assertEquals(0.5, TemperatureCalc.nextWetness(0.4, 0.1), 1.0e-9)
  }

  // NOTE: the 35.0/39.5 band and 5.0 slopes mirror CasualtiesBelowConfig defaults; keep in sync
  // when calibrating.
  @Test
  def penaltyBandDeviationsAreZeroInsideAndLinearOutside(): Unit = {
    assertEquals(0.0, TemperatureCalc.coldDeviation(36.5, 35.0), 1.0e-9, "inside the band")
    assertEquals(0.0, TemperatureCalc.hotDeviation(36.5, 39.5), 1.0e-9, "inside the band")
    assertEquals(0.0, TemperatureCalc.coldDeviation(35.0, 35.0), 1.0e-9, "lower bound included")
    assertEquals(0.0, TemperatureCalc.hotDeviation(39.5, 39.5), 1.0e-9, "upper bound included")
    assertEquals(2.0, TemperatureCalc.coldDeviation(33.0, 35.0), 1.0e-9)
    assertEquals(0.5, TemperatureCalc.hotDeviation(40.0, 39.5), 1.0e-9)
    assertEquals(
      0.0,
      TemperatureCalc.coldDeviation(40.0, 35.0),
      1.0e-9,
      "cold deviation ignores hot readings"
    )
  }

  @Test
  def defaultCeilingFormulaSubtractsBothSides(): Unit = {
    val ceiling = (coldDev: Double, hotDev: Double) =>
      evaluate(
        Consts.Temperature.TemperatureConsciousnessCeilingFormula.source,
        Seq("coldDev", "hotDev", "coldSlope", "hotSlope"),
        coldDev,
        hotDev,
        5.0,
        5.0
      )
    assertEquals(100.0, ceiling(0.0, 0.0), 1.0e-9, "in the band the ceiling stays at max")
    assertEquals(90.0, ceiling(2.0, 0.0), 1.0e-9, "33°C caps consciousness at 90")
    assertEquals(97.5, ceiling(0.0, 0.5), 1.0e-9, "40°C caps consciousness at 97.5")
    assertEquals(75.0, ceiling(5.0, 0.0), 1.0e-9, "30°C caps consciousness at 75")
    assertEquals(87.5, ceiling(2.0, 0.5), 1.0e-9, "both sides sum")
  }

  @Test
  def immuneDrainConvertsMinutesToTicks(): Unit = {
    assertEquals(
      2.0 * 1.0 / 1200.0,
      TemperatureCalc.immuneDrainPerTick(2.0, 1.0),
      1.0e-12,
      "2°C cold deviation at 1.0/min drains 1/600 per tick"
    )
    assertEquals(
      0.5 * 0.5 / 1200.0,
      TemperatureCalc.immuneDrainPerTick(0.5, 0.5),
      1.0e-12,
      "hot side converts with its own coefficient"
    )
    assertEquals(0.0, TemperatureCalc.immuneDrainPerTick(0.0, 5.0), 1.0e-12)
  }

  // ---- Helpers -----------------------------------------------------------------

  private def biomeMapping(t: Double): Double =
    evaluate(Consts.Temperature.BiomeMappingFormula.source, Seq("t"), t)

  private def comfortBand(t: Double): Double =
    evaluate(
      Consts.Temperature.ComfortBandFormula.source,
      Seq("t", "low", "high", "slope"),
      t,
      10.0,
      28.0,
      0.3
    )

  @Test
  def sweatRateFractionScalesToSprintAndSaturates(): Unit = {
    assertEquals(
      0.0,
      TemperatureCalc.sweatRateFraction(0.0, 0.56),
      1.0e-9,
      "no exertion, no sweat"
    )
    assertEquals(
      0.5,
      TemperatureCalc.sweatRateFraction(0.28, 0.56),
      1.0e-9,
      "half sprint exertion gives half the sweat rate"
    )
    assertEquals(
      1.0,
      TemperatureCalc.sweatRateFraction(0.56, 0.56),
      1.0e-9,
      "sprint reference exertion gives the full sweat rate"
    )
    assertEquals(
      1.0,
      TemperatureCalc.sweatRateFraction(5.0, 0.56),
      1.0e-9,
      "beyond the sprint reference the sweat rate saturates"
    )
    assertEquals(
      0.0,
      TemperatureCalc.sweatRateFraction(-1.0, 0.56),
      1.0e-9,
      "a negative exertion signal must not sweat"
    )
  }

  private def collapse(wetness: Double): Double =
    evaluate(Consts.Temperature.WetnessCollapseFormula.source, Seq("wetness"), wetness)

  private def drying(t: Double): Double =
    evaluate(Consts.Temperature.DryingCurveFormula.source, Seq("t"), t)

  private def evaluate(source: String, variables: Seq[String], values: Double*): Double = {
    val expression = FormulaConfigValue.compile(source, variables)
    variables.lazyZip(values).foreach((name, value) => expression.`with`(name, value))
    expression.evaluate().getNumberValue().doubleValue()
  }
}
