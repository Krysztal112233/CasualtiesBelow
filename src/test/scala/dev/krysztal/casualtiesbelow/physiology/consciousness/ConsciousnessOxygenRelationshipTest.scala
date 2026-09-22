package dev.krysztal.casualtiesbelow.physiology.consciousness

import dev.krysztal.casualtiesbelow.api.body.vitals.ConsciousnessSnapshot
import dev.krysztal.casualtiesbelow.api.body.vitals.PainShockStage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

final class ConsciousnessOxygenRelationshipTest {
  private val RecoveryThreshold = 75.0
  private val CapMultiplier = 1.2
  private val RecoveryPerTick = 0.2
  private val WakeThreshold = 40.0
  private val KnockoutThreshold = 30.0
  private val Floor = 10.0

  @Test
  def oxygenAppliesAHardCeilingWithoutAnAdditionalDrain(): Unit = {
    val pressure = hypoxia(50.0)
    assertEquals(60.0, pressure.ceiling, 1.0e-9)
    assertEquals(0.0, pressure.lossPerTick, 1.0e-9)
    assertTrue(pressure.recoveryBlocked)
    assertTrue(pressure.wakeBlocked)

    val step = advance(100.0, unconscious = false, pressure)
    assertEquals(60.0, step.level, 1.0e-9)
    assertFalse(step.unconscious)
  }

  @Test
  def knockoutThresholdIsIndependentFromTheOrdinaryFloor(): Unit = {
    val atThreshold = Consciousness.reconcile(
      KnockoutThreshold,
      unconscious = false,
      List(ConsciousnessPressure()),
      WakeThreshold,
      KnockoutThreshold,
      Floor
    )
    assertEquals(KnockoutThreshold, atThreshold.level, 1.0e-9)
    assertTrue(atThreshold.unconscious)

    val aboveThreshold = Consciousness.reconcile(
      Math.nextUp(KnockoutThreshold),
      unconscious = false,
      List(ConsciousnessPressure()),
      WakeThreshold,
      KnockoutThreshold,
      Floor
    )
    assertFalse(aboveThreshold.unconscious)
  }

  @Test
  def wakingRequiresBothHysteresisAndEnoughOxygen(): Unit = {
    val belowWake = Consciousness.reconcile(
      Math.nextDown(WakeThreshold),
      unconscious = true,
      List(ConsciousnessPressure()),
      WakeThreshold,
      KnockoutThreshold,
      Floor
    )
    assertTrue(belowWake.unconscious)

    val oxygenBlocked = Consciousness.reconcile(
      WakeThreshold,
      unconscious = true,
      List(hypoxia(60.0)),
      WakeThreshold,
      KnockoutThreshold,
      Floor
    )
    assertTrue(oxygenBlocked.unconscious)

    val recovered = Consciousness.reconcile(
      WakeThreshold,
      unconscious = true,
      List(hypoxia(75.0)),
      WakeThreshold,
      KnockoutThreshold,
      Floor
    )
    assertFalse(recovered.unconscious)
  }

  @Test
  def defaultDrowningRateReachesKnockoutAfterOneHundredEightyEightTicks(): Unit = {
    var oxygen = 100.0
    var consciousness = 100.0
    var unconscious = false
    var knockoutTick = 0

    (1 to 250).foreach { tick =>
      oxygen = (oxygen - 0.4).max(0.0)
      val step = advance(consciousness, unconscious, hypoxia(oxygen))
      consciousness = step.level
      unconscious = step.unconscious
      if (unconscious && knockoutTick == 0) knockoutTick = tick
    }

    assertEquals(188, knockoutTick)
    assertEquals(Floor, consciousness, 1.0e-9)
    assertTrue(unconscious)
  }

  @Test
  def storedStableStateUsesKnockoutThresholdWhilePainShockRecoveryKeepsLiteralRange(): Unit = {
    val stable = Consciousness.normalizeStoredState(
      25.0,
      Some(false),
      Floor,
      KnockoutThreshold,
      PainShockStage.Stable
    )
    assertEquals(25.0, stable.level, 1.0e-9)
    assertTrue(stable.unconscious)

    val recovering = Consciousness.normalizeStoredState(
      5.0,
      Some(false),
      Floor,
      KnockoutThreshold,
      PainShockStage.Recovering
    )
    assertEquals(5.0, recovering.level, 1.0e-9)
    assertTrue(recovering.unconscious)
  }

  @Test
  def invalidNumbersNormalizeConservativelyWithoutPoisoningState(): Unit = {
    val invalidOxygen = hypoxia(Double.NaN)
    assertEquals(0.0, invalidOxygen.ceiling, 1.0e-9)
    assertTrue(invalidOxygen.recoveryBlocked)

    val invalidCurrent = Consciousness.advance(
      Double.NaN,
      unconscious = false,
      List(ConsciousnessPressure()),
      RecoveryPerTick,
      WakeThreshold,
      KnockoutThreshold,
      Floor
    )
    assertEquals(Floor, invalidCurrent.level, 1.0e-9)
    assertTrue(invalidCurrent.unconscious)

    val invalidPressure = Consciousness.advance(
      100.0,
      unconscious = false,
      List(ConsciousnessPressure(lossPerTick = Double.NaN, ceiling = Double.NaN)),
      RecoveryPerTick,
      WakeThreshold,
      KnockoutThreshold,
      Floor
    )
    assertEquals(Floor, invalidPressure.level, 1.0e-9)
    assertTrue(invalidPressure.unconscious)
  }

  @Test
  def aMaximumKnockoutThresholdCannotOscillateIntoAnImpossibleWake(): Unit = {
    val entered = Consciousness.reconcile(
      100.0,
      unconscious = false,
      List(ConsciousnessPressure()),
      wakeThreshold = 100.0,
      knockoutThreshold = 100.0,
      floor = Floor
    )
    assertTrue(entered.unconscious)

    val remained = Consciousness.reconcile(
      entered.level,
      entered.unconscious,
      List(ConsciousnessPressure()),
      wakeThreshold = 100.0,
      knockoutThreshold = 100.0,
      floor = Floor
    )
    assertTrue(remained.unconscious)
  }

  @Test
  def synchronizedClientStateTrustsTheServerLatch(): Unit = {
    val synchronized = Consciousness.normalizeSyncedState(
      0.0,
      Some(false),
      PainShockStage.Stable
    )
    assertEquals(0.0, synchronized.level, 1.0e-9)
    assertFalse(synchronized.unconscious)
  }

  @Test
  def painShockRecoveryUsesTheStableWakeThresholdBeforeReturningToStable(): Unit = {
    val stillRecovering = Consciousness.reconcile(
      5.0,
      unconscious = true,
      List(ConsciousnessPressure()),
      wakeThreshold = WakeThreshold,
      knockoutThreshold = 0.0,
      floor = 0.0
    )
    assertTrue(stillRecovering.unconscious)

    val readyForStable = Consciousness.reconcile(
      WakeThreshold,
      unconscious = true,
      List(ConsciousnessPressure()),
      wakeThreshold = WakeThreshold,
      knockoutThreshold = 0.0,
      floor = 0.0
    )
    assertFalse(readyForStable.unconscious)
  }

  private def hypoxia(oxygen: Double): ConsciousnessPressure = {
    Consciousness.hypoxiaPressure(oxygen, RecoveryThreshold, CapMultiplier)
  }

  private def advance(
      consciousness: Double,
      unconscious: Boolean,
      pressure: ConsciousnessPressure
  ): ConsciousnessSnapshot = {
    Consciousness.advance(
      consciousness,
      unconscious,
      List(pressure),
      RecoveryPerTick,
      WakeThreshold,
      KnockoutThreshold,
      Floor
    )
  }
}
