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

  @Test
  def heatIsZeroAtOrBelowOnset(): Unit = {
    assertEquals(0.0, TemperatureVisuals.heatStrength(39.5, 39.5, 2.5, 0.85), 1.0e-9)
    assertEquals(
      0.0,
      TemperatureVisuals.heatStrength(37.0, 39.5, 2.5, 0.85),
      1.0e-9,
      "a normal body must not simmer"
    )
  }

  @Test
  def heatRampsLinearlyThroughTheSpan(): Unit = {
    assertEquals(
      0.85 / 2.0,
      TemperatureVisuals.heatStrength(40.75, 39.5, 2.5, 0.85),
      1.0e-9,
      "half the span gives half the strength"
    )
    assertEquals(
      0.85,
      TemperatureVisuals.heatStrength(42.0, 39.5, 2.5, 0.85),
      1.0e-9,
      "full strength lands on the terminal-band edge"
    )
    assertEquals(0.85, TemperatureVisuals.heatStrength(45.0, 39.5, 2.5, 0.85), 1.0e-9)
  }

  @Test
  def heatHandlesDegenerateSpan(): Unit = {
    assertEquals(0.85, TemperatureVisuals.heatStrength(40.0, 39.5, 0.0, 0.85), 1.0e-9)
    assertEquals(0.0, TemperatureVisuals.heatStrength(39.5, 39.5, 0.0, 0.85), 1.0e-9)
  }

  @Test
  def frostAndHeatAreMutuallyExclusive(): Unit = {
    // The bands share one body temperature: whatever the temperature, at most one side renders.
    Vector(20.0, 30.0, 34.0, 37.0, 40.0, 42.5, 45.0).foreach { t =>
      val frost = TemperatureVisuals.frostStrength(t, 35.0, 6.0, 0.85)
      val heat = TemperatureVisuals.heatStrength(t, 39.5, 2.5, 0.85)
      assertTrue(frost == 0.0 || heat == 0.0, s"both overlays active at $t°C")
    }
  }
}
