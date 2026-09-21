package dev.krysztal.casualtiesbelow.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Unit coverage for the temperature presentation curves ([[TemperatureVisuals]]): anchors,
  * clamping, and continuity of the frost ramp.
  */
final class TemperatureVisualsTest {

  @Test
  def frostIsZeroAtOrAboveOnset(): Unit = {
    assertEquals(0.0, TemperatureVisuals.frostStrength(35.0, 35.0, 6.0, 0.85), 1.0e-9)
    assertEquals(
      0.0,
      TemperatureVisuals.frostStrength(36.5, 35.0, 6.0, 0.85),
      1.0e-9,
      "a warm body must not frost"
    )
  }

  @Test
  def frostRampsLinearlyThroughTheSpan(): Unit = {
    assertEquals(
      0.85 / 6.0,
      TemperatureVisuals.frostStrength(34.0, 35.0, 6.0, 0.85),
      1.0e-9,
      "one degree into a 6°C span"
    )
    assertEquals(
      0.85 / 2.0,
      TemperatureVisuals.frostStrength(32.0, 35.0, 6.0, 0.85),
      1.0e-9,
      "half the span gives half the strength"
    )
  }

  @Test
  def frostClampsAtFullSpanAndBeyond(): Unit = {
    assertEquals(0.85, TemperatureVisuals.frostStrength(29.0, 35.0, 6.0, 0.85), 1.0e-9)
    assertEquals(0.85, TemperatureVisuals.frostStrength(20.0, 35.0, 6.0, 0.85), 1.0e-9)
  }

  @Test
  def frostRampIsContinuousAcrossTheSpan(): Unit = {
    // The linear ramp's Lipschitz constant is maxStrength/span; sweep the whole span and bound
    // every step accordingly (continuity, not just the anchors).
    val samples = 1000
    val start = 35.0
    val span = 6.0
    val maxStrength = 0.85
    val maxStep = maxStrength / span + 1.0e-9
    (0 until samples).foreach { i =>
      val a = start - span * (i.toDouble / samples)
      val b = start - span * ((i + 1).toDouble / samples)
      val step = math.abs(
        TemperatureVisuals.frostStrength(b, start, span, maxStrength) -
          TemperatureVisuals.frostStrength(a, start, span, maxStrength)
      )
      assertTrue(step <= maxStep + 1.0e-9, s"frost ramp jumped by $step near $a°C")
    }
  }

  @Test
  def frostHandlesDegenerateSpan(): Unit = {
    // A zero/negative span config is clamped to a hairline ramp instead of dividing by zero.
    assertEquals(0.85, TemperatureVisuals.frostStrength(34.0, 35.0, 0.0, 0.85), 1.0e-9)
    assertEquals(0.0, TemperatureVisuals.frostStrength(35.0, 35.0, 0.0, 0.85), 1.0e-9)
  }
}
