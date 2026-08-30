package dev.krysztal.casualtiesbelow.adrenaline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

final class AdrenalineStateTest {

  @Test
  def grantsAccumulateClampAndRefreshGrace(): Unit = {
    val first = Adrenaline.grantState(AdrenalineState.Empty, 10.0, 25.0, 2)
    assertEquals(AdrenalineState(10.0, 3), first)

    val aged = Adrenaline.advanceState(first, 25.0, 0.5)
    val refreshed = Adrenaline.grantState(aged, 20.0, 25.0, 2)
    assertEquals(AdrenalineState(25.0, 3), refreshed)

    val zeroOverride = Adrenaline.grantState(aged, 0.0, 25.0, 2)
    assertEquals(aged, zeroOverride)
  }

  @Test
  def aFreshGrantSurvivesItsTickAndExactlyTheConfiguredGrace(): Unit = {
    (0 to 2).foreach { configuredGrace =>
      var state =
        Adrenaline.grantState(AdrenalineState.Empty, 5.0, 100.0, configuredGrace)
      assertEquals(configuredGrace + 1, state.graceTicks)

      (0 to configuredGrace).foreach { _ =>
        state = Adrenaline.advanceState(state, 100.0, 1.0)
        assertEquals(5.0, state.amount, 1.0e-9)
      }
      assertEquals(0, state.graceTicks)

      state = Adrenaline.advanceState(state, 100.0, 1.0)
      assertEquals(4.0, state.amount, 1.0e-9)
    }
  }

  @Test
  def decayIsLinearAndStopsAtZero(): Unit = {
    val first = Adrenaline.advanceState(AdrenalineState(1.5, 0), 100.0, 1.0)
    val second = Adrenaline.advanceState(first, 100.0, 1.0)
    val third = Adrenaline.advanceState(second, 100.0, 1.0)

    assertEquals(AdrenalineState(0.5, 0), first)
    assertEquals(AdrenalineState.Empty, second)
    assertEquals(AdrenalineState.Empty, third)
  }

  @Test
  def malformedAndOutOfRangeStateIsNormalized(): Unit = {
    assertEquals(AdrenalineState.Empty, Adrenaline.normalizeState(Double.NaN, -1, 100.0))
    assertEquals(
      AdrenalineState.Empty,
      Adrenaline.normalizeState(Double.NegativeInfinity, 5, 100.0)
    )
    assertEquals(
      AdrenalineState(100.0, 5),
      Adrenaline.normalizeState(Double.PositiveInfinity, 5, 100.0)
    )
    assertEquals(AdrenalineState(100.0, 0), Adrenaline.normalizeState(150.0, -5, 100.0))

    val stored = Adrenaline.normalizeStoredState(50.0, Int.MaxValue, 75.0, 100)
    assertEquals(101, stored.graceTicks)

    // Network application intentionally does not apply the receiving client's configured maximum.
    assertEquals(AdrenalineState(150.0, 7), Adrenaline.normalizeSyncedState(150.0, 7))
  }
}
