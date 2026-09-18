package dev.krysztal.casualtiesbelow.progression

import dev.krysztal.casualtiesbelow.progression.HemostasisEpisode.State

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

final class HemostasisEpisodeTest {

  @Test
  def armsAtThreshold(): Unit = {
    assertEquals((State.Armed, false), HemostasisEpisode.next(State.Idle, 0.75, 0.75))
  }

  @Test
  def staysIdleBelowThreshold(): Unit = {
    assertEquals((State.Idle, false), HemostasisEpisode.next(State.Idle, 0.74, 0.75))
  }

  @Test
  def armedCompletesOnlyAtFullStop(): Unit = {
    assertEquals((State.Idle, true), HemostasisEpisode.next(State.Armed, 0.0, 0.75))
    assertEquals((State.Armed, false), HemostasisEpisode.next(State.Armed, 0.001, 0.75))
  }

  @Test
  def zeroThresholdNeverArms(): Unit = {
    assertEquals((State.Idle, false), HemostasisEpisode.next(State.Idle, 0.0, 0.0))
    assertEquals((State.Idle, false), HemostasisEpisode.next(State.Idle, 1.0, 0.0))
  }

  @Test
  def fullEpisodeRunsArmThenComplete(): Unit = {
    val (armed, completedEarly) = HemostasisEpisode.next(State.Idle, 1.0, 0.75)
    assertEquals((State.Armed, false), (armed, completedEarly))
    val (stillArmed, _) = HemostasisEpisode.next(armed, 0.4, 0.75)
    assertEquals(State.Armed, stillArmed)
    assertEquals((State.Idle, true), HemostasisEpisode.next(stillArmed, 0.0, 0.75))
  }
}
