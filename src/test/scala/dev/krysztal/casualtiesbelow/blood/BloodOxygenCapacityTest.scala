package dev.krysztal.casualtiesbelow.blood

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

final class BloodOxygenCapacityTest {

  @Test
  def healthyAndModeratelyReducedBloodRetainFullCapacity(): Unit = {
    assertCapacity(100.0, bloodVolume = 5000.0)
    assertCapacity(100.0, bloodVolume = 3750.0)
    assertCapacity(100.0, bloodVolume = 3000.0)
  }

  @Test
  def capacityFallsLinearlyBelowSixtyPercentBlood(): Unit = {
    assertCapacity(100.0 * 2500.0 / 3000.0, bloodVolume = 2500.0)
    assertCapacity(100.0 * 2000.0 / 3000.0, bloodVolume = 2000.0)
    assertCapacity(50.0, bloodVolume = 1500.0)
    assertCapacity(25.0, bloodVolume = 750.0)
    assertCapacity(0.0, bloodVolume = 0.0)
  }

  @Test
  def invalidInputsRemainBounded(): Unit = {
    assertEquals(0.0, BloodVolume.oxygenCarryingCapacity(1000.0, 0.0, 0.6), 1.0e-9)
    assertEquals(0.0, BloodVolume.oxygenCarryingCapacity(Double.NaN, 5000.0, 0.6), 1.0e-9)
    assertEquals(
      100.0,
      BloodVolume.oxygenCarryingCapacity(Double.PositiveInfinity, 5000.0, 0.6),
      1.0e-9
    )
  }

  private def assertCapacity(expected: Double, bloodVolume: Double): Unit = {
    assertEquals(
      expected,
      BloodVolume.oxygenCarryingCapacity(bloodVolume, 5000.0, 0.6),
      1.0e-9
    )
  }
}
