package dev.krysztal.casualtiesbelow.physiology.adrenaline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

final class AdrenalinePainTest {

  @Test
  def defaultCurveReducesPainFromThePostGrantReserve(): Unit = {
    assertEquals(1.0, multiplier(0.0), 1.0e-9)
    assertEquals(0.95, multiplier(10.0), 1.0e-9)
    assertEquals(0.85, multiplier(30.0), 1.0e-9)
    assertEquals(0.8, multiplier(40.0), 1.0e-9)

    val afterCurrentHit =
      Adrenaline.grantState(AdrenalineState(20.0, 0), 30.0, maximum = 100.0, combatGraceTicks = 100)
    assertEquals(50.0, afterCurrentHit.amount, 1.0e-9)
    assertEquals(0.75, multiplier(afterCurrentHit.amount), 1.0e-9)
  }

  @Test
  def maximumReductionLeavesHalfOfAcutePain(): Unit = {
    assertEquals(0.5, multiplier(100.0), 1.0e-9)
    assertEquals(0.5, multiplier(1000.0), 1.0e-9)
    assertEquals(0.5, multiplier(Double.PositiveInfinity), 1.0e-9)
  }

  @Test
  def disabledAndMalformedInputsCannotInventAnalgesia(): Unit = {
    assertEquals(1.0, AdrenalinePain.multiplier(100.0, 0.0, 0.5), 1.0e-9)
    assertEquals(1.0, AdrenalinePain.multiplier(100.0, 0.005, 0.0), 1.0e-9)
    assertEquals(1.0, AdrenalinePain.multiplier(-10.0, 0.005, 0.5), 1.0e-9)
    assertEquals(1.0, AdrenalinePain.multiplier(Double.NaN, 0.005, 0.5), 1.0e-9)
    assertEquals(1.0, AdrenalinePain.multiplier(100.0, Double.NaN, 0.5), 1.0e-9)
    assertEquals(1.0, AdrenalinePain.multiplier(100.0, 0.005, Double.NaN), 1.0e-9)
  }

  @Test
  def externallySuppliedMultipliersAreBounded(): Unit = {
    assertEquals(0.0, AdrenalinePain.normalizeMultiplier(-1.0), 1.0e-9)
    assertEquals(0.25, AdrenalinePain.normalizeMultiplier(0.25), 1.0e-9)
    assertEquals(1.0, AdrenalinePain.normalizeMultiplier(2.0), 1.0e-9)
    assertEquals(1.0, AdrenalinePain.normalizeMultiplier(Double.NaN), 1.0e-9)
    assertEquals(20.0, AdrenalinePain.scale(40.0, 0.5), 1.0e-9)
    assertEquals(-1.5, AdrenalinePain.scale(-3.0, 0.5), 1.0e-9)
    assertEquals(0.0, AdrenalinePain.scale(Double.PositiveInfinity, 0.0), 1.0e-9)
  }

  private def multiplier(adrenaline: Double): Double =
    AdrenalinePain.multiplier(adrenaline, reductionPerPoint = 0.005, maxReductionFraction = 0.5)
}
