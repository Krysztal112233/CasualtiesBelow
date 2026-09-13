package dev.krysztal.casualtiesbelow.item

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

final class InjectionSessionTest {

  @Test
  def fractionalProgressAccumulatesUntilAWholeDropletFlushes(): Unit = {
    val session = new InjectionSession(810L, 810L)
    session.advance(0.5, 0.4)
    assertTrue(session.flush().isEmpty, "sub-droplet progress must not flush")
    session.advance(0.5, 0.4)
    session.advance(0.5, 0.4)
    val batch = session.flush().get
    assertEquals(1L, batch.droplets)
    assertEquals(0.5, batch.averageSpeed, 1.0e-12)
  }

  @Test
  def multiBatchCumulativeEqualsSingleFullPush(): Unit = {
    val batched = new InjectionSession(810L, 810L)
    var total = 0L
    var remaining = 810.0
    while (remaining > 0.0) {
      val step = remaining.min(83.7)
      batched.advance(1.0, step)
      batched.flush().foreach(batch => total += batch.droplets)
      remaining -= step
    }
    batched.flush().foreach(batch => total += batch.droplets)
    assertEquals(810L, total)

    val single = new InjectionSession(810L, 810L)
    single.advance(1.0, 810.0)
    assertEquals(810L, single.flush().get.droplets)
  }

  @Test
  def finalFlushEmptiesExactlyWithNoResidue(): Unit = {
    val session = new InjectionSession(810L, 810L)
    session.advance(1.0, 809.6)
    assertEquals(809L, session.flush().get.droplets)
    session.advance(1.0, 0.4)
    assertTrue(session.fullyInjected)
    val last = session.flush().get
    assertEquals(1L, last.droplets, "the final flush reports the exact remaining droplets")
    assertTrue(session.flush().isEmpty, "an emptied session flushes nothing more")
  }

  @Test
  def advanceIsClampedToTheRemainingAmount(): Unit = {
    val session = new InjectionSession(810L, 810L)
    session.advance(1.0, 5000.0)
    assertTrue(session.fullyInjected)
    assertEquals(810L, session.flush().get.droplets)
  }

  @Test
  def resumedSessionReportsOnlyItsRemainder(): Unit = {
    val session = new InjectionSession(810L, 405L)
    session.advance(1.0, 405.0)
    assertTrue(session.fullyInjected)
    assertEquals(405L, session.flush().get.droplets)
  }

  @Test
  def averageSpeedIsDropletWeighted(): Unit = {
    val session = new InjectionSession(810L, 810L)
    session.advance(1.0, 100.0)
    session.advance(0.0, 100.0)
    session.flush()
    session.advance(1.0, 300.0)
    session.advance(0.5, 100.0)
    val batch = session.flush().get
    assertEquals(400L, batch.droplets)
    assertEquals((300.0 * 1.0 + 100.0 * 0.5) / 400.0, batch.averageSpeed, 1.0e-12)
  }

  @Test
  def expectedStackDropletsTracksOnlyFlushedReports(): Unit = {
    val session = new InjectionSession(810L, 810L)
    assertEquals(810L, session.expectedStackDroplets)
    session.advance(1.0, 100.0)
    assertEquals(
      810L,
      session.expectedStackDroplets,
      "unflushed progress must not move the reported baseline"
    )
    session.flush()
    assertEquals(710L, session.expectedStackDroplets)
    session.advance(1.0, 710.0)
    session.flush()
    assertEquals(0L, session.expectedStackDroplets)
  }

  @Test
  def expectedStackDropletsReflectsResumedSessionContents(): Unit = {
    val session = new InjectionSession(810L, 405L)
    assertEquals(405L, session.expectedStackDroplets)
  }
}
