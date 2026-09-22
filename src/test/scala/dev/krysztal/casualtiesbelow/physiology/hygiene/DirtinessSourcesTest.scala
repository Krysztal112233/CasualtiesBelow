package dev.krysztal.casualtiesbelow.physiology.dirtiness

import net.minecraft.util.RandomSource

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

final class DirtinessSourcesTest {

  @Test
  def hitDirtPrefersZombieOverExplosionOverMonster(): Unit = {
    assertEquals(3.0, DirtinessSources.hitDirt(true, true, true, 3.0, 5.0, 1.0), 1.0e-12)
    assertEquals(5.0, DirtinessSources.hitDirt(false, true, true, 3.0, 5.0, 1.0), 1.0e-12)
    assertEquals(1.0, DirtinessSources.hitDirt(false, false, true, 3.0, 5.0, 1.0), 1.0e-12)
  }

  @Test
  def hitDirtIgnoresNonMonsterHits(): Unit = {
    assertEquals(0.0, DirtinessSources.hitDirt(false, false, false, 3.0, 5.0, 1.0), 1.0e-12)
  }

  @Test
  def foodDirtIsTheFractionOfTheMean(): Unit = {
    assertEquals(3.0, DirtinessSources.foodDirt(30.0, 0.1), 1.0e-12)
    assertEquals(1.5, DirtinessSources.foodDirt(15.0, 0.1), 1.0e-12)
  }

  @Test
  def foodDirtNeverTurnsNegative(): Unit = {
    assertEquals(0.0, DirtinessSources.foodDirt(-5.0, 0.1), 1.0e-12)
    assertEquals(0.0, DirtinessSources.foodDirt(5.0, -0.1), 1.0e-12)
  }

  @Test
  def digPulseTiersDirtyAboveBasicAboveDustless(): Unit = {
    assertEquals(0.04, DirtinessSources.digPulse(true, false, 0.04, 0.0, 0.02), 1.0e-12)
    assertEquals(0.02, DirtinessSources.digPulse(false, false, 0.04, 0.0, 0.02), 1.0e-12)
    assertEquals(0.0, DirtinessSources.digPulse(false, true, 0.04, 0.0, 0.02), 1.0e-12)
  }

  @Test
  def digPulsePrefersDirtyWhenBothTagsMatch(): Unit = {
    assertEquals(0.04, DirtinessSources.digPulse(true, true, 0.04, 0.0, 0.02), 1.0e-12)
  }

  @Test
  def rolledPulsesStayWithinTheJitterBounds(): Unit = {
    val random = RandomSource.create(42L)
    (1 to 1000).foreach { _ =>
      val rolled = DirtinessSources.rollPulse(3.0, 0.3, random)
      assertTrue(rolled >= 3.0 * 0.7 - 1.0e-9, s"below jitter floor: $rolled")
      assertTrue(rolled <= 3.0 * 1.3 + 1.0e-9, s"above jitter ceiling: $rolled")
    }
  }

  @Test
  def zeroJitterKeepsTheBase(): Unit = {
    val random = RandomSource.create(42L)
    assertEquals(3.0, DirtinessSources.rollPulse(3.0, 0.0, random), 1.0e-12)
  }
}

final class DirtinessFormulasTest {

  @Test
  def infectionChanceRampsFromOneToOnePlusAtMax(): Unit = {
    assertEquals(1.0, Dirtiness.infectionChanceMultiplier(0.0, 100.0, 2.0), 1.0e-12)
    assertEquals(2.0, Dirtiness.infectionChanceMultiplier(50.0, 100.0, 2.0), 1.0e-12)
    assertEquals(3.0, Dirtiness.infectionChanceMultiplier(100.0, 100.0, 2.0), 1.0e-12)
  }

  @Test
  def infectionChanceClampsBeyondTheAxis(): Unit = {
    assertEquals(3.0, Dirtiness.infectionChanceMultiplier(150.0, 100.0, 2.0), 1.0e-12)
    assertEquals(1.0, Dirtiness.infectionChanceMultiplier(50.0, 0.0, 2.0), 1.0e-12)
  }

  @Test
  def skinRegenRampsDownButNeverToZero(): Unit = {
    assertEquals(1.0, Dirtiness.skinRegenMultiplier(0.0, 100.0, 0.75), 1.0e-12)
    assertEquals(0.75, Dirtiness.skinRegenMultiplier(100.0, 100.0, 0.75), 1.0e-12)
    assertEquals(0.875, Dirtiness.skinRegenMultiplier(50.0, 100.0, 0.75), 1.0e-12)
  }

  @Test
  def immuneDrainStartsAtTheKneeAndRampsToMax(): Unit = {
    assertEquals(0.0, Dirtiness.immuneDrainPerTick(50.0, 50.0, 100.0, 0.01), 1.0e-12)
    assertEquals(0.005, Dirtiness.immuneDrainPerTick(75.0, 50.0, 100.0, 0.01), 1.0e-12)
    assertEquals(0.01, Dirtiness.immuneDrainPerTick(100.0, 50.0, 100.0, 0.01), 1.0e-12)
    assertEquals(0.0, Dirtiness.immuneDrainPerTick(30.0, 50.0, 100.0, 0.01), 1.0e-12)
  }

  @Test
  def degenerateDrainRangeDisablesTheDrain(): Unit = {
    assertEquals(0.0, Dirtiness.immuneDrainPerTick(100.0, 100.0, 100.0, 0.01), 1.0e-12)
  }

  @Test
  def foodDiscomfortRampsFromOneToOnePlusAtMax(): Unit = {
    assertEquals(1.0, Dirtiness.foodDiscomfortMultiplier(0.0, 100.0, 0.5), 1.0e-12)
    assertEquals(1.5, Dirtiness.foodDiscomfortMultiplier(100.0, 100.0, 0.5), 1.0e-12)
  }
}
