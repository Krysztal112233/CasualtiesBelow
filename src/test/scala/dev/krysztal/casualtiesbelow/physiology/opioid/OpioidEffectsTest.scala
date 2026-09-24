package dev.krysztal.casualtiesbelow.physiology.opioid

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

final class OpioidEffectsTest {

  @Test
  def effectiveLevelDampsByDependenceAndDrainScalesWithIt(): Unit = {
    assertEquals(100.0, OpioidEffects.effectiveLevel(100.0, 0.0), 1.0e-9)
    assertEquals(50.0, OpioidEffects.effectiveLevel(100.0, 100.0), 1.0e-9)
    assertEquals(0.0, OpioidEffects.painDrainPerTick(0.0, 0.0, 0.001), 1.0e-12)
    // Default coefficient 0.001: effective 100 drains 0.1 pain/tick, dependence halves it.
    assertEquals(0.1, OpioidEffects.painDrainPerTick(100.0, 0.0, 0.001), 1.0e-12)
    assertEquals(0.05, OpioidEffects.painDrainPerTick(100.0, 100.0, 0.001), 1.0e-12)
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
