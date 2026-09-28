package dev.krysztal.casualtiesbelow.physiology.discomfort

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Pure-math coverage for [[DiscomfortCalc]]: decay pacing and the chance ramp. */
class DiscomfortCalcTest {

  // ---- Decay ----

  @Test
  def decayIsFastBelowThresholdAndSlowAbove(): Unit = {
    assertEquals(
      9.975,
      DiscomfortCalc.nextAfterOrdinaryDecay(10.0, false, 30.0, 0.5, 0.2),
      1.0e-9,
      "below the threshold the low decay rate applies (0.5/s over one tick)"
    )
    assertEquals(
      39.99,
      DiscomfortCalc.nextAfterOrdinaryDecay(40.0, false, 30.0, 0.5, 0.2),
      1.0e-9,
      "above the threshold the high decay rate applies (0.2/s over one tick)"
    )
  }

  @Test
  def withdrawalFreezesOrdinaryDecay(): Unit = {
    assertEquals(
      12.34,
      DiscomfortCalc.nextAfterOrdinaryDecay(12.34, true, 30.0, 0.5, 0.2),
      1.0e-9,
      "withdrawal owns discomfort evolution while active"
    )
  }

  @Test
  def decayFloorsAtZero(): Unit = {
    assertEquals(
      0.0,
      DiscomfortCalc.nextAfterOrdinaryDecay(0.001, false, 30.0, 0.5, 0.2),
      1.0e-9,
      "decay never pushes discomfort negative"
    )
  }

  // ---- Chance ramp ----

  @Test
  def chanceIsZeroAtOrBelowThreshold(): Unit = {
    assertEquals(0.0, DiscomfortCalc.chancePerTick(30.0, 30.0, 0.0005, 0.00256, 100.0), 1.0e-12)
    assertEquals(0.0, DiscomfortCalc.chancePerTick(10.0, 30.0, 0.0005, 0.00256, 100.0), 1.0e-12)
  }

  @Test
  def chanceRisesLinearlyWithDiscomfort(): Unit = {
    assertEquals(
      0.00153,
      DiscomfortCalc.chancePerTick(65.0, 30.0, 0.0005, 0.00256, 100.0),
      1.0e-9,
      "mid-band chance is the midpoint of the ramp"
    )
    assertEquals(
      0.00256,
      DiscomfortCalc.chancePerTick(100.0, 30.0, 0.0005, 0.00256, 100.0),
      1.0e-12,
      "full discomfort reaches the configured max"
    )
  }

  @Test
  def chanceClampsBeyondMaxDiscomfort(): Unit = {
    assertEquals(
      0.00256,
      DiscomfortCalc.chancePerTick(150.0, 30.0, 0.0005, 0.00256, 100.0),
      1.0e-12,
      "progress clamps at 1 beyond the axis"
    )
  }
}
