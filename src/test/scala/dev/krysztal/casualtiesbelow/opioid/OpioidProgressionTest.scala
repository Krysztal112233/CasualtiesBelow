package dev.krysztal.casualtiesbelow.opioid

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

final class OpioidProgressionTest {

  @Test
  def acuteLevelDecaysLinearlyToZero(): Unit = {
    val first = nextState(100.0, 0.0)
    assertEquals(99.9917, first.level, 1.0e-9)

    val ticksToEmpty = math.ceil(100.0 / LevelDecayPerTick).toInt
    val empty = Iterator
      .iterate(OpioidState(100.0, 0.0))(state => nextState(state.level, state.dependence))
      .drop(ticksToEmpty)
      .next()
    assertEquals(0.0, empty.level, 1.0e-9)
  }

  @Test
  def integratedDependenceScalesWithDoseSquared(): Unit = {
    val lowDoseDependence = exposureDependence(50.0)
    val standardDoseDependence = exposureDependence(100.0)

    assertEquals(4.0, standardDoseDependence / lowDoseDependence, 0.001)
    assertEquals(7.53, standardDoseDependence, 0.01)
  }

  @Test
  def dependenceDecaysThreePointsPerMinecraftDayWithoutExposure(): Unit = {
    val afterDay = Iterator
      .iterate(OpioidState(0.0, 10.0))(state => nextState(state.level, state.dependence))
      .drop(24000)
      .next()

    assertEquals(7.0, afterDay.dependence, 1.0e-8)
  }

  @Test
  def withdrawalUsesStrictDependenceAndLevelBoundaries(): Unit = {
    assertFalse(OpioidWithdrawal.isActive(0.0, 20.0, 20.0, 0.6))
    assertFalse(OpioidWithdrawal.isActive(30.0, 50.0, 20.0, 0.6))
    assertTrue(OpioidWithdrawal.isActive(29.999, 50.0, 20.0, 0.6))
    assertTrue(OpioidWithdrawal.isActive(0.0, 20.001, 20.0, 0.6))
  }

  private def exposureDependence(initialLevel: Double): Double = {
    Iterator
      .iterate(OpioidState(initialLevel, 0.0))(state => nextState(state.level, state.dependence))
      .dropWhile(_.level > 0.0)
      .next()
      .dependence
  }

  private def nextState(level: Double, dependence: Double): OpioidState = {
    OpioidProgression.nextState(
      level,
      dependence,
      LevelDecayPerTick,
      ExposurePerLevelPerTick,
      DependenceDecayPerTick
    )
  }

  private val LevelDecayPerTick = 0.0083
  private val ExposurePerLevelPerTick = 0.0000125
  private val DependenceDecayPerTick = 0.000125
}
