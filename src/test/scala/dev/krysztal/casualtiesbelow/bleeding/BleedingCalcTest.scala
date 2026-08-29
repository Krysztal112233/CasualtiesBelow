package dev.krysztal.casualtiesbelow.bleeding

import dev.krysztal.casualtiesbelow.api.body.LimbStats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

final class BleedingCalcTest {

  @Test
  def hemostasisReducesTheCurrentRateByFraction(): Unit = {
    val stats = LimbStats(externalBleedingRate = 0.2)

    BleedingCalc.applyHemostasis(stats, 0.25)

    assertEquals(0.15, stats.externalBleedingRate, 1.0e-9)
  }

  @Test
  def fullHemostasisClampsTheRateToZero(): Unit = {
    val stats = LimbStats(externalBleedingRate = 0.2)

    BleedingCalc.applyHemostasis(stats, 1.0)

    assertEquals(0.0, stats.externalBleedingRate, 1.0e-9)
  }
}
