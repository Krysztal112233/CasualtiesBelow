package dev.krysztal.casualtiesbelow.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

final class HypoxiaVisualsTest {

  @Test
  def oxygenColorHitsItsAnchorsExactly(): Unit = {
    assertEquals(0x0058d8e8, HypoxiaVisuals.oxygenColor(1.0))
    assertEquals(0x00ffb347, HypoxiaVisuals.oxygenColor(0.5))
    assertEquals(0x00ff4f55, HypoxiaVisuals.oxygenColor(0.25))
    assertEquals(0x00ff2e3f, HypoxiaVisuals.oxygenColor(0.0))
  }

  @Test
  def oxygenColorMovesContinuouslyBetweenAnchors(): Unit = {
    (0 to 999).foreach { i =>
      val fraction = i.toDouble / 1000.0
      assertChannelStepBounded(
        HypoxiaVisuals.oxygenColor(fraction),
        HypoxiaVisuals.oxygenColor(fraction + 0.001)
      )
    }
  }

  @Test
  def bubbleFillsSumToTheReserveFraction(): Unit = {
    List(1.0, 0.996, 0.77, 0.5, 0.25, 0.05, 0.001, 0.0).foreach { fraction =>
      val fills = HypoxiaVisuals.bubbleFills(fraction)
      assertEquals(HypoxiaVisuals.OxygenSegmentCount, fills.size)
      assertEquals(fraction * HypoxiaVisuals.OxygenSegmentCount, fills.sum, 1.0e-9)
      assertTrue(fills.forall(fill => fill >= 0.0 && fill <= 1.0))
    }
    assertEquals(0.0, HypoxiaVisuals.bubbleFills(Double.NaN).sum, 0.0)
  }

  @Test
  def bubbleFillsFillFromTheRight(): Unit = {
    // Index 0 is the rightmost bubble and fills first, so fills never increase along the row.
    List(0.83, 0.5, 0.31, 0.02).foreach { fraction =>
      val fills = HypoxiaVisuals.bubbleFills(fraction)
      fills.zip(fills.drop(1)).foreach { (current, next) => assertTrue(current >= next) }
    }
  }

  @Test
  def oxygenPulseStaysSilentAboveHalfReserve(): Unit = {
    List(1.0, 0.75, 0.5).foreach { fraction =>
      assertEquals(0.0, HypoxiaVisuals.oxygenPulseFrequency(fraction), 0.0)
      assertEquals(1.0, HypoxiaVisuals.oxygenPulseFloor(fraction), 0.0)
    }
  }

  @Test
  def oxygenPulseParametersRampContinuouslyAndStayBounded(): Unit = {
    (0 to 499).foreach { i =>
      val fraction = i.toDouble / 1000.0
      val frequency = HypoxiaVisuals.oxygenPulseFrequency(fraction)
      val floor = HypoxiaVisuals.oxygenPulseFloor(fraction)
      assertTrue(frequency >= 0.0 && frequency <= 2.25 + 1.0e-9)
      assertTrue(floor >= 0.5 - 1.0e-9 && floor <= 1.0)
      val neighborFrequency = HypoxiaVisuals.oxygenPulseFrequency(fraction + 0.001)
      val neighborFloor = HypoxiaVisuals.oxygenPulseFloor(fraction + 0.001)
      // Analytic bound: steepest ramp is 1.5 Hz over 0.25 fraction with smoothstep's maximum
      // derivative 1.875, i.e. ~11.25 Hz per unit fraction — 0.01125 per 0.001 step.
      assertTrue(math.abs(neighborFrequency - frequency) <= 0.02)
      assertTrue(math.abs(neighborFloor - floor) <= 0.01)
    }
  }

  @Test
  def pulseOpacitySweepsBetweenFloorAndFullOpacity(): Unit = {
    val floor = 0.6
    assertEquals(floor, HypoxiaVisuals.pulseOpacity(0.75, floor), 1.0e-9)
    assertEquals(1.0, HypoxiaVisuals.pulseOpacity(0.25, floor), 1.0e-9)
    (0 to 100).foreach { i =>
      val opacity = HypoxiaVisuals.pulseOpacity(i.toDouble / 100.0, floor)
      assertTrue(opacity >= floor - 1.0e-9 && opacity <= 1.0 + 1.0e-9)
    }
  }

  @Test
  def terminalColorHitsItsAnchorsExactly(): Unit = {
    assertEquals(0x0058d8e8, HypoxiaVisuals.terminalColor(1.0))
    assertEquals(0x00ffb347, HypoxiaVisuals.terminalColor(0.25))
    assertEquals(0x00ff4f55, HypoxiaVisuals.terminalColor(0.0))
  }

  @Test
  def terminalColorWarmsContinuouslyAsTimeRunsOut(): Unit = {
    (0 to 999).foreach { i =>
      val remaining = i.toDouble / 1000.0
      assertChannelStepBounded(
        HypoxiaVisuals.terminalColor(remaining),
        HypoxiaVisuals.terminalColor(remaining + 0.001)
      )
    }
  }

  @Test
  def terminalPulseStaysSilentAboveQuarterRemaining(): Unit = {
    List(1.0, 0.5, 0.25).foreach { remaining =>
      assertEquals(0.0, HypoxiaVisuals.terminalPulseFrequency(remaining), 0.0)
      assertEquals(1.0, HypoxiaVisuals.terminalPulseFloor(remaining), 0.0)
    }
  }

  @Test
  def terminalPulseParametersRampContinuouslyAndStayBounded(): Unit = {
    (0 to 249).foreach { i =>
      val remaining = i.toDouble / 1000.0
      val frequency = HypoxiaVisuals.terminalPulseFrequency(remaining)
      val floor = HypoxiaVisuals.terminalPulseFloor(remaining)
      assertTrue(frequency >= 0.0 && frequency <= 2.5 + 1.0e-9)
      assertTrue(floor >= 0.46 - 1.0e-9 && floor <= 1.0)
      val neighborFrequency = HypoxiaVisuals.terminalPulseFrequency(remaining + 0.001)
      val neighborFloor = HypoxiaVisuals.terminalPulseFloor(remaining + 0.001)
      assertTrue(math.abs(neighborFrequency - frequency) <= 0.02)
      assertTrue(math.abs(neighborFloor - floor) <= 0.02)
    }
  }

  @Test
  def terminalRemainingFractionFollowsContinuousExposure(): Unit = {
    assertEquals(1.0, HypoxiaVisuals.terminalRemainingFraction(0.0, 160), 1.0e-9)
    assertEquals(149.5 / 160.0, HypoxiaVisuals.terminalRemainingFraction(10.5, 160), 1.0e-9)
    assertEquals(0.5, HypoxiaVisuals.terminalRemainingFraction(80.0, 160), 1.0e-9)
    assertEquals(0.0, HypoxiaVisuals.terminalRemainingFraction(160.0, 160), 1.0e-9)
    assertEquals(0.0, HypoxiaVisuals.terminalRemainingFraction(999.0, 160), 1.0e-9)
    assertEquals(1.0, HypoxiaVisuals.terminalRemainingFraction(-3.0, 160), 1.0e-9)
    assertEquals(1.0, HypoxiaVisuals.terminalRemainingFraction(Double.NaN, 160), 1.0e-9)
  }

  @Test
  def staggerOffsetsSpreadAcrossLessThanOneCycle(): Unit = {
    val offsets = (0 until HypoxiaVisuals.OxygenSegmentCount).map(HypoxiaVisuals.staggerOffset)
    assertEquals(0.0, offsets.head, 0.0)
    offsets.zip(offsets.drop(1)).foreach { (current, next) => assertTrue(next > current) }
    assertTrue(offsets.last < 1.0)
  }

  private def assertChannelStepBounded(first: Int, second: Int): Unit = {
    val channelStep = (0 to 2).map { channel =>
      val shift = channel * 8
      math.abs(((first >> shift) & 0xff) - ((second >> shift) & 0xff))
    }.max
    assertTrue(channelStep <= 2, s"color step too large: $first vs $second")
  }
}
