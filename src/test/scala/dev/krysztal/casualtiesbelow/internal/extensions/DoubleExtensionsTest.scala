package dev.krysztal.casualtiesbelow.internal.extensions

import dev.krysztal.casualtiesbelow.internal.extensions.DoubleExtensions.*

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Pure-math coverage for the sampling extensions on [[DoubleExtensions]]. */
class DoubleExtensionsTest {

  // ---- jittered ----

  @Test
  def jitteredHitsBaseAndBounds(): Unit = {
    assertEquals(30.0, 30.0.jittered(0.2, 0.5), 1.0e-9, "mid roll is the base itself")
    assertEquals(24.0, 30.0.jittered(0.2, 0.0), 1.0e-9, "low roll is base × (1 - fraction)")
    assertEquals(36.0, 30.0.jittered(0.2, 1.0), 1.0e-9, "high roll is base × (1 + fraction)")
  }

  // ---- gaussianSample ----

  @Test
  def gaussianSampleScalesWithAbsMean(): Unit = {
    assertEquals(30.0, 30.0.gaussianSample(0.2, 0.0), 1.0e-9, "zero gaussian is the mean")
    assertEquals(36.0, 30.0.gaussianSample(0.2, 1.0), 1.0e-9, "one sigma up is mean + spread")
    assertEquals(
      -24.0,
      (-30.0).gaussianSample(0.2, 1.0),
      1.0e-9,
      "spread magnitude follows |mean|, so +1 sigma moves a negative mean toward zero"
    )
  }

  // ---- ramp01 ----

  @Test
  def ramp01TracksLinearProgress(): Unit = {
    assertEquals(0.0, 5.0.ramp01(10.0, 50.0), 1.0e-12, "below the start clamps to 0")
    assertEquals(0.5, 30.0.ramp01(10.0, 50.0), 1.0e-12, "mid ramp is 0.5")
    assertEquals(1.0, 80.0.ramp01(10.0, 50.0), 1.0e-12, "past full clamps to 1")
    assertEquals(1.0, 4.0.ramp01(3.0, 3.0), 1.0e-12, "a zero-width ramp is treated as width 1")
  }
}
