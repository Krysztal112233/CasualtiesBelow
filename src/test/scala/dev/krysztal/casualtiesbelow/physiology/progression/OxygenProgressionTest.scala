package dev.krysztal.casualtiesbelow.physiology.progression

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

final class OxygenProgressionTest {

  @Test
  def reducedBloodCapacityClampsStoredOxygenImmediately(): Unit = {
    assertEquals(
      50.0,
      OxygenProgression.nextBloodOxygen(100.0, 50.0, false, 0.4, 0.8),
      1.0e-9
    )
    assertEquals(
      49.6,
      OxygenProgression.nextBloodOxygen(100.0, 50.0, true, 0.4, 0.8),
      1.0e-9
    )
  }

  @Test
  def opioidEfficiencyScalesRecoveryAndFailureReplacesItWithNetDrain(): Unit = {
    assertEquals(
      50.4,
      OxygenProgression.nextBloodOxygen(50.0, 100.0, false, 0.4, 0.8, 0.5, false, 0.0),
      1.0e-9
    )
    assertEquals(
      49.7,
      OxygenProgression.nextBloodOxygen(50.0, 100.0, false, 0.4, 0.8, 0.1, true, 0.3),
      1.0e-9
    )
  }

  @Test
  def respiratoryFailureWithZeroDrainStillPreventsRecovery(): Unit = {
    assertEquals(
      50.0,
      OxygenProgression.nextBloodOxygen(50.0, 100.0, false, 0.4, 0.8, 0.1, true, 0.0),
      1.0e-9
    )
  }

  @Test
  def deprivationAndRecoveryStayInsideCapacity(): Unit = {
    assertEquals(
      0.0,
      OxygenProgression.nextBloodOxygen(0.2, 100.0, true, 0.4, 0.8),
      1.0e-9
    )
    assertEquals(
      50.0,
      OxygenProgression.nextBloodOxygen(49.6, 50.0, false, 0.4, 0.8),
      1.0e-9
    )
    assertEquals(
      50.0,
      OxygenProgression.nextBloodOxygen(Double.PositiveInfinity, 50.0, false, 0.4, 0.8),
      1.0e-9
    )
  }
}
