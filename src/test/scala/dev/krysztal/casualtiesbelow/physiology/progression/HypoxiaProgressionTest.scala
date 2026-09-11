package dev.krysztal.casualtiesbelow.physiology.progression

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

final class HypoxiaProgressionTest {

  @Test
  def terminalExposureSynchronizesAtSemanticMilestones(): Unit = {
    val started = HypoxiaProgression.advance(0, 0.0, respirationFailed = true, 160)
    assertEquals(1, started.exposureTicks)
    assertTrue(started.changed)
    assertTrue(started.syncDue)
    assertFalse(started.fatal)

    val ordinary = HypoxiaProgression.advance(1, 0.0, respirationFailed = true, 160)
    assertEquals(2, ordinary.exposureTicks)
    assertTrue(ordinary.changed)
    assertFalse(ordinary.syncDue)

    val oneSecond = HypoxiaProgression.advance(19, 0.0, respirationFailed = true, 160)
    assertEquals(20, oneSecond.exposureTicks)
    assertTrue(oneSecond.syncDue)

    val fatal = HypoxiaProgression.advance(159, 0.0, respirationFailed = true, 160)
    assertEquals(160, fatal.exposureTicks)
    assertTrue(fatal.syncDue)
    assertTrue(fatal.fatal)
  }

  @Test
  def restoredRespirationResetsAndSynchronizesExposureImmediately(): Unit = {
    val reset = HypoxiaProgression.advance(87, 0.0, respirationFailed = false, 160)
    assertEquals(0, reset.exposureTicks)
    assertTrue(reset.changed)
    assertTrue(reset.syncDue)
    assertFalse(reset.fatal)

    val alreadyReset = HypoxiaProgression.advance(0, 0.0, respirationFailed = false, 160)
    assertFalse(alreadyReset.changed)
    assertFalse(alreadyReset.syncDue)
  }

  @Test
  def positiveBloodOxygenCannotStartTerminalExposure(): Unit = {
    val preserved = HypoxiaProgression.advance(0, 0.01, respirationFailed = true, 160)
    assertEquals(0, preserved.exposureTicks)
    assertFalse(preserved.changed)
    assertFalse(preserved.syncDue)
    assertFalse(preserved.fatal)
  }

  @Test
  def aOneTickConfiguredDurationStillPublishesItsFatalEndpoint(): Unit = {
    val fatal = HypoxiaProgression.advance(0, 0.0, respirationFailed = true, 0)
    assertEquals(1, fatal.exposureTicks)
    assertTrue(fatal.changed)
    assertTrue(fatal.syncDue)
    assertTrue(fatal.fatal)
  }
}
