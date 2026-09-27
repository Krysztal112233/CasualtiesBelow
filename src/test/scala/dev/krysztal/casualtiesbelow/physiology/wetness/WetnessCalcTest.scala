package dev.krysztal.casualtiesbelow.physiology.wetness

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap

import dev.krysztal.casualtiesbelow.config.FormulaConfigValue
import dev.krysztal.casualtiesbelow.internal.Consts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Unit coverage for the pure wetness steps ([[WetnessCalc]]) and the default drying curve.
  *
  * The default formula source is compiled straight from
  * [[dev.krysztal.casualtiesbelow.internal.Consts.Wetness]] because config values cannot be read
  * without a loaded config file; evaluating through [[FormulaConfigValue.compile]] still exercises
  * the real EvalEx pipeline the game uses.
  */
final class WetnessCalcTest {

  SharedConstants.tryDetectVersion()
  Bootstrap.bootStrap()

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

  @Test
  def wetnessStepClampsToTheAxis(): Unit = {
    assertEquals(1.0, WetnessCalc.nextWetness(0.9, 0.2), 1.0e-9)
    assertEquals(0.0, WetnessCalc.nextWetness(0.1, -0.2), 1.0e-9)
    assertEquals(0.5, WetnessCalc.nextWetness(0.4, 0.1), 1.0e-9)
  }

  @Test
  def sweatRateFractionScalesToSprintAndSaturates(): Unit = {
    assertEquals(
      0.0,
      WetnessCalc.sweatRateFraction(0.0, 0.56),
      1.0e-9,
      "no exertion, no sweat"
    )
    assertEquals(
      0.5,
      WetnessCalc.sweatRateFraction(0.28, 0.56),
      1.0e-9,
      "half sprint exertion gives half the sweat rate"
    )
    assertEquals(
      1.0,
      WetnessCalc.sweatRateFraction(0.56, 0.56),
      1.0e-9,
      "sprint reference exertion gives the full sweat rate"
    )
    assertEquals(
      1.0,
      WetnessCalc.sweatRateFraction(5.0, 0.56),
      1.0e-9,
      "beyond the sprint reference the sweat rate saturates"
    )
    assertEquals(
      0.0,
      WetnessCalc.sweatRateFraction(-1.0, 0.56),
      1.0e-9,
      "a negative exertion signal must not sweat"
    )
  }

  private def drying(t: Double): Double =
    evaluate(Consts.Wetness.DryingCurveFormula.source, Seq("t"), t)

  private def evaluate(source: String, variables: Seq[String], values: Double*): Double = {
    val expression = FormulaConfigValue.compile(source, variables)
    variables.lazyZip(values).foreach((name, value) => expression.`with`(name, value))
    expression.evaluate().getNumberValue().doubleValue()
  }
}
