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
    assertTrue(snowyPlains < 0.2, s"frozen clothing must barely dry, got $snowyPlains")
    val onFire = drying(17.0 + 60.0)
    assertTrue(onFire > 5.0, s"on fire must dry wetness in seconds, got $onFire")
    assertTrue(
      drying(40.0) > drying(17.0) && drying(17.0) > drying(-3.2),
      "drying rate must grow with temperature"
    )
  }

  @Test
  def wetnessStepClampsToTheAxis(): Unit = {
    assertEquals(100.0, WetnessCalc.nextWetness(90.0, 20.0), 1.0e-9)
    assertEquals(0.0, WetnessCalc.nextWetness(10.0, -20.0), 1.0e-9)
    assertEquals(50.0, WetnessCalc.nextWetness(40.0, 10.0), 1.0e-9)
  }

  @Test
  def sweatRateIsLinearAboveThresholdAndZeroBelow(): Unit = {
    assertEquals(
      0.0,
      WetnessCalc.sweatRatePerSecond(36.5, 37.0, 1.0),
      1.0e-9,
      "cool core, no sweat"
    )
    assertEquals(
      0.0,
      WetnessCalc.sweatRatePerSecond(37.0, 37.0, 1.0),
      1.0e-9,
      "at threshold, no sweat"
    )
    assertEquals(0.5, WetnessCalc.sweatRatePerSecond(37.5, 37.0, 1.0), 1.0e-9, "half a degree over")
    assertEquals(
      5.0,
      WetnessCalc.sweatRatePerSecond(42.0, 37.0, 1.0),
      1.0e-9,
      "crisis core gives 5/s at slope 1"
    )
    assertEquals(
      4.0,
      WetnessCalc.sweatRatePerSecond(39.0, 37.0, 2.0),
      1.0e-9,
      "slope scales linearly"
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
