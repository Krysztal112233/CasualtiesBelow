package dev.krysztal.casualtiesbelow.physiology.pain

import dev.krysztal.casualtiesbelow.api.body.vitals.PainShockStage
import dev.krysztal.casualtiesbelow.physiology.progression.ConsciousnessProgression

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

final class PainShockAdrenalineTest {
  private val Base = 90.0
  private val Effective = 95.0

  @Test
  def stagesFollowTheEpisodeLifecycleOrder(): Unit = {
    assertEquals(0, PainShockStage.Stable.ordinal)
    assertEquals(1, PainShockStage.Deferred.ordinal)
    assertEquals(2, PainShockStage.Collapsed.ordinal)
    assertEquals(3, PainShockStage.Recovering.ordinal)
  }

  @Test
  def stableUsesBaseAndEffectiveThresholds(): Unit = {
    assertEquals(
      PainShockStage.Stable,
      transition(PainShockStage.Stable, Math.nextDown(Base))
    )
    assertEquals(PainShockStage.Deferred, transition(PainShockStage.Stable, Base))
    assertEquals(
      PainShockStage.Deferred,
      transition(PainShockStage.Stable, Math.nextDown(Effective))
    )
    assertEquals(PainShockStage.Collapsed, transition(PainShockStage.Stable, Effective))
  }

  @Test
  def deferredCancelsBelowBaseAndCollapsesAtEffective(): Unit = {
    val cancelled = transition(PainShockStage.Deferred, Math.nextDown(Base))
    assertEquals(PainShockStage.Stable, cancelled)
    assertEquals(
      PainShockStage.Stable,
      PainShock.transition(cancelled, Math.nextDown(Base), Math.nextDown(Base), Base, Base)
    )

    assertEquals(PainShockStage.Deferred, transition(PainShockStage.Deferred, Base))
    assertEquals(
      PainShockStage.Deferred,
      transition(PainShockStage.Deferred, Math.nextDown(Effective))
    )
    assertEquals(PainShockStage.Collapsed, transition(PainShockStage.Deferred, Effective))
  }

  @Test
  def protectionCanExceedMaximumLoadAndZeroProtectionDoesNothing(): Unit = {
    assertEquals(110.0, PainShock.effectiveCollapseThreshold(Base, 20.0, 1.0), 1.0e-9)
    assertEquals(
      PainShockStage.Deferred,
      PainShock.transition(PainShockStage.Stable, 99.0, 100.0, Base, 110.0)
    )
    assertEquals(Base, PainShock.effectiveCollapseThreshold(Base, 20.0, 0.0), 1.0e-9)
    assertEquals(
      PainShockStage.Collapsed,
      PainShock.transition(PainShockStage.Stable, 89.0, Base, Base, Base)
    )
  }

  @Test
  def collapsedAndRecoveringIgnoreAdrenalineProtection(): Unit = {
    assertEquals(
      PainShockStage.Recovering,
      PainShock.transition(PainShockStage.Collapsed, 95.0, Base, Base, 200.0)
    )
    assertEquals(
      PainShockStage.Collapsed,
      PainShock.transition(PainShockStage.Collapsed, Base, Base, Base, 200.0)
    )
    assertEquals(
      PainShockStage.Collapsed,
      PainShock.transition(PainShockStage.Recovering, Math.nextDown(Base), Base, Base, 200.0)
    )
  }

  @Test
  def deferredUsesOrdinaryConsciousnessRules(): Unit = {
    val normalized = ConsciousnessProgression.normalizeStoredState(
      75.0,
      Some(false),
      floor = 10.0,
      knockoutThreshold = 30.0,
      painShockStage = PainShockStage.Deferred
    )
    assertEquals(75.0, normalized.level, 1.0e-9)
    assertFalse(normalized.unconscious)
  }

  private def transition(stage: PainShockStage, load: Double): PainShockStage = {
    PainShock.transition(stage, load, load, Base, Effective)
  }
}
