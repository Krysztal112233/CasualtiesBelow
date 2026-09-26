package dev.krysztal.casualtiesbelow.physiology.circulation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Behavioral pins for terminal hypoxia exposure: accumulation while respiration fails at zero
  * blood oxygen, immediate reset when breathing resumes, and the fatal endpoint. Synchronization is
  * the component's concern (any change marks the owner dirty) and deliberately unpinned here.
  */
final class HypoxiaProgressionTest {

  @Test
  def terminalExposureAccumulatesToTheFatalEndpoint(): Unit = {
    val started = HypoxiaProgression.advance(0, 0.0, respirationFailed = true, 160)
    assertEquals(1, started.exposureTicks)
    assertFalse(started.fatal)

    val ordinary = HypoxiaProgression.advance(1, 0.0, respirationFailed = true, 160)
    assertEquals(2, ordinary.exposureTicks)
    assertFalse(ordinary.fatal)

    val capped = HypoxiaProgression.advance(159, 0.0, respirationFailed = true, 160)
    assertEquals(160, capped.exposureTicks)
    assertTrue(capped.fatal)

    val beyondCap = HypoxiaProgression.advance(160, 0.0, respirationFailed = true, 160)
    assertEquals(160, beyondCap.exposureTicks)
    assertTrue(beyondCap.fatal)
  }

  @Test
  def restoredRespirationResetsExposureImmediately(): Unit = {
    val reset = HypoxiaProgression.advance(87, 0.0, respirationFailed = false, 160)
    assertEquals(0, reset.exposureTicks)
    assertFalse(reset.fatal)

    val alreadyReset = HypoxiaProgression.advance(0, 0.0, respirationFailed = false, 160)
    assertEquals(0, alreadyReset.exposureTicks)
  }

  @Test
  def positiveBloodOxygenCannotStartTerminalExposure(): Unit = {
    val preserved = HypoxiaProgression.advance(0, 0.01, respirationFailed = true, 160)
    assertEquals(0, preserved.exposureTicks)
    assertFalse(preserved.fatal)

    val frozen = HypoxiaProgression.advance(42, 0.01, respirationFailed = true, 160)
    assertEquals(42, frozen.exposureTicks)
    assertFalse(frozen.fatal)
  }

  @Test
  def aOneTickConfiguredDurationStillPublishesItsFatalEndpoint(): Unit = {
    val fatal = HypoxiaProgression.advance(0, 0.0, respirationFailed = true, 0)
    assertEquals(1, fatal.exposureTicks)
    assertTrue(fatal.fatal)
  }
}
