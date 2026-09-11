package dev.krysztal.casualtiesbelow.physiology.bleeding

import dev.krysztal.casualtiesbelow.component.MutableLimbState

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

final class BleedingCalcTest {

  @Test
  def hemostasisReducesTheCurrentRateByFraction(): Unit = {
    val stats = MutableLimbState(externalBleedingRate = 0.2)

    BleedingCalc.applyHemostasis(stats, 0.25)

    assertEquals(0.15, stats.externalBleedingRate, 1.0e-9)
  }

  @Test
  def fullHemostasisClampsTheRateToZero(): Unit = {
    val stats = MutableLimbState(externalBleedingRate = 0.2)

    BleedingCalc.applyHemostasis(stats, 1.0)

    assertEquals(0.0, stats.externalBleedingRate, 1.0e-9)
  }

  @Test
  def fixedHemostasisSubtractsAnAbsoluteRate(): Unit = {
    val stats = MutableLimbState(externalBleedingRate = 0.2)

    BleedingCalc.applyFixedHemostasis(stats, 0.05)

    assertEquals(0.15, stats.externalBleedingRate, 1.0e-9)
  }

  @Test
  def fixedHemostasisCannotIncreaseOrMakeBleedingNegative(): Unit = {
    val stats = MutableLimbState(externalBleedingRate = 0.05)

    BleedingCalc.applyFixedHemostasis(stats, -1.0)
    assertEquals(0.05, stats.externalBleedingRate, 1.0e-9)

    BleedingCalc.applyFixedHemostasis(stats, 0.1)
    assertEquals(0.0, stats.externalBleedingRate, 1.0e-9)
  }
}
