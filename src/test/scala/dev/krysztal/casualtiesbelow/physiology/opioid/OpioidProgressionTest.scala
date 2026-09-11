package dev.krysztal.casualtiesbelow.physiology.opioid

import dev.krysztal.casualtiesbelow.physiology.discomfort.Discomfort

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
  def integratedDependenceIncludesUnconditionalDecay(): Unit = {
    assertEquals(1.13, exposureDependence(50.0), 0.01)
    assertEquals(6.02, exposureDependence(100.0), 0.01)
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
  def exposureAndDecayApplyInTheSameTick(): Unit = {
    val next = OpioidProgression.nextState(50.0, 10.0, 0.0083, 0.0000125, 0.000125)
    assertEquals(10.0005, next.dependence, 1.0e-12)
  }

  @Test
  def withdrawalDiscomfortRisesWhileOrdinaryDiscomfortStillDecays(): Unit = {
    val afterWithdrawal = Iterator
      .iterate(10.0) { discomfort =>
        val gained = OpioidProgression.nextWithdrawalDiscomfort(discomfort, 0.0025, 35.0)
        Discomfort.nextAfterOrdinaryDecay(gained, true, 30.0, 0.5, 0.2)
      }
      .drop(400)
      .next()
    assertEquals(11.0, afterWithdrawal, 1.0e-9)

    assertEquals(
      9.975,
      Discomfort.nextAfterOrdinaryDecay(10.0, false, 30.0, 0.5, 0.2),
      1.0e-9
    )
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
