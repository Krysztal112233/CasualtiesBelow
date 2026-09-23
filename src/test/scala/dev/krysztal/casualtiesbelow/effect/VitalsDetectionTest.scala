package dev.krysztal.casualtiesbelow.effect

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

final class VitalsDetectionTest {

  @Test
  def opioidLevelMapsToFiveEqualAmplifierBands(): Unit = {
    assertEquals(None, amplifierForValue(0.0, 200.0, 5))
    assertEquals(Some(0), amplifierForValue(0.1, 200.0, 5))
    assertEquals(Some(0), amplifierForValue(40.0, 200.0, 5))
    assertEquals(Some(1), amplifierForValue(40.1, 200.0, 5))
    assertEquals(Some(2), amplifierForValue(100.0, 200.0, 5))
    assertEquals(Some(4), amplifierForValue(200.0, 200.0, 5))
  }

  @Test
  def dependenceMapsToThreeEqualAmplifierBands(): Unit = {
    val firstBand = 100.0 / 3.0
    val secondBand = 200.0 / 3.0

    assertEquals(None, amplifierForValue(0.0, 100.0, 3))
    assertEquals(Some(0), amplifierForValue(firstBand, 100.0, 3))
    assertEquals(Some(1), amplifierForValue(firstBand + 0.01, 100.0, 3))
    assertEquals(Some(1), amplifierForValue(secondBand, 100.0, 3))
    assertEquals(Some(2), amplifierForValue(100.0, 100.0, 3))
  }

  @Test
  def nonPositiveAndNonFiniteValuesDoNotProduceAnEffect(): Unit = {
    assertEquals(None, amplifierForValue(-1.0, 200.0, 5))
    assertEquals(None, amplifierForValue(Double.NaN, 200.0, 5))
    assertEquals(None, amplifierForValue(Double.PositiveInfinity, 200.0, 5))
  }
}
