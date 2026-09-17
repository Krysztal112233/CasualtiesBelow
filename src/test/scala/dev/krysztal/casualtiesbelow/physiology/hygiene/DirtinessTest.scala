package dev.krysztal.casualtiesbelow.physiology.hygiene

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

final class DirtinessTest {

  @Test
  def baseAccrualIsThePerSecondBasePerTick(): Unit = {
    assertEquals(
      0.0015,
      Dirtiness.accrualPerTick(
        0.03,
        2.5,
        1.3,
        1.5,
        sprinting = false,
        fullyArmored = false,
        inNether = false
      ),
      1.0e-12
    )
  }

  @Test
  def situationalMultipliersStackMultiplicatively(): Unit = {
    assertEquals(
      0.0015 * 2.5 * 1.3 * 1.5,
      Dirtiness.accrualPerTick(
        0.03,
        2.5,
        1.3,
        1.5,
        sprinting = true,
        fullyArmored = true,
        inNether = true
      ),
      1.0e-12
    )
  }

  @Test
  def immersionWinsOverRain(): Unit = {
    assertEquals(
      0.24,
      Dirtiness.washPerTick(
        4.8,
        0.6,
        0.5,
        inWater = true,
        inRain = true,
        murkyWater = false
      ),
      1.0e-12
    )
  }

  @Test
  def murkyWaterDampensOnlyImmersion(): Unit = {
    assertEquals(
      0.12,
      Dirtiness.washPerTick(
        4.8,
        0.6,
        0.5,
        inWater = true,
        inRain = false,
        murkyWater = true
      ),
      1.0e-12
    )
  }

  @Test
  def rainAppliesOnlyWithoutImmersion(): Unit = {
    assertEquals(
      0.03,
      Dirtiness.washPerTick(
        4.8,
        0.6,
        0.5,
        inWater = false,
        inRain = true,
        murkyWater = false
      ),
      1.0e-12
    )
    assertEquals(
      0.0,
      Dirtiness.washPerTick(
        4.8,
        0.6,
        0.5,
        inWater = false,
        inRain = false,
        murkyWater = false
      ),
      1.0e-12
    )
  }

  @Test
  def negativeRatesClampToZero(): Unit = {
    assertEquals(
      0.0,
      Dirtiness.accrualPerTick(
        -1.0,
        2.5,
        1.3,
        1.5,
        sprinting = true,
        fullyArmored = true,
        inNether = true
      ),
      1.0e-12
    )
    assertEquals(
      0.0,
      Dirtiness.washPerTick(
        -1.0,
        -1.0,
        0.5,
        inWater = true,
        inRain = true,
        murkyWater = false
      ),
      1.0e-12
    )
  }
}
