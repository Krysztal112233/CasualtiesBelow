package dev.krysztal.casualtiesbelow.physiology.opioid

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

final class OpioidEffectsTest {

  @Test
  def analgesiaMasksFeltPainWithoutExceedingItsCap(): Unit = {
    assertEquals(1.0 / 3.0, OpioidEffects.analgesiaFraction(50.0, 0.0), 1.0e-9)
    assertEquals(2.0 / 3.0, OpioidEffects.analgesiaFraction(100.0, 0.0), 1.0e-9)
    assertEquals(1.0 / 3.0, OpioidEffects.analgesiaFraction(100.0, 100.0), 1.0e-9)
    assertEquals(0.85, OpioidEffects.analgesiaFraction(200.0, 0.0), 1.0e-9)
    assertEquals(33.3333333333, OpioidEffects.feltPain(100.0, 100.0, 0.0), 1.0e-8)
  }

  @Test
  def sedationOnlyLowersTheConsciousnessCeilingAboveOnset(): Unit = {
    assertEquals(100.0, OpioidEffects.consciousnessCeiling(110.0), 1.0e-9)
    assertEquals(95.0, OpioidEffects.consciousnessCeiling(120.0), 1.0e-9)
    assertEquals(55.0, OpioidEffects.consciousnessCeiling(200.0), 1.0e-9)
  }

  @Test
  def respiratoryToleranceMovesOnsetButNotTheSlope(): Unit = {
    assertEquals(1.0, OpioidEffects.respiratoryEfficiency(120.0, 0.0), 1.0e-9)
    assertEquals(0.1, OpioidEffects.respiratoryEfficiency(200.0, 0.0), 1.0e-9)
    assertEquals(1.0, OpioidEffects.respiratoryEfficiency(140.0, 100.0), 1.0e-9)
    assertEquals(0.325, OpioidEffects.respiratoryEfficiency(200.0, 100.0), 1.0e-9)
    assertTrue(OpioidEffects.causesRespiratoryFailure(0.2999, 0.3))
    assertFalse(OpioidEffects.causesRespiratoryFailure(0.3, 0.3))
  }
}
