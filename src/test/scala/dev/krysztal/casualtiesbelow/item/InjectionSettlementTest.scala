package dev.krysztal.casualtiesbelow.item

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

final class InjectionSettlementTest {

  @Test
  def zeroSpeedYieldsZeroSideEffects(): Unit = {
    assertEquals(
      0.0,
      InjectionSettlement.sideEffect(10.0, 0.0, 810L, 810L),
      1.0e-12
    )
  }

  @Test
  def fullSpeedFullSyringeYieldsExactlyTheCaps(): Unit = {
    assertEquals(10.0, InjectionSettlement.sideEffect(10.0, 1.0, 810L, 810L), 1.0e-12)
    assertEquals(7.5, InjectionSettlement.sideEffect(7.5, 1.0, 810L, 810L), 1.0e-12)
  }

  @Test
  def sideEffectsScaleLinearlyWithSpeedAndAmount(): Unit = {
    assertEquals(
      5.0,
      InjectionSettlement.sideEffect(10.0, 0.5, 810L, 810L),
      1.0e-12,
      "half speed at full amount"
    )
    assertEquals(
      5.0,
      InjectionSettlement.sideEffect(10.0, 1.0, 405L, 810L),
      1.0e-12,
      "full speed at half amount"
    )
    assertEquals(
      2.5,
      InjectionSettlement.sideEffect(10.0, 0.5, 405L, 810L),
      1.0e-12,
      "half speed at half amount"
    )
  }

  @Test
  def clampingRejectsOverRemainingDropletsAndOverCapSpeed(): Unit = {
    assertEquals(400L, InjectionSettlement.clampDroplets(810L, 400L))
    assertEquals(0L, InjectionSettlement.clampDroplets(-5L, 400L))
    assertEquals(1.0, InjectionSettlement.clampSpeed(7.25), 1.0e-12)
    assertEquals(0.0, InjectionSettlement.clampSpeed(Double.NaN), 1.0e-12)
    // An over-cap speed settles at the cap, i.e. the full-dose side effect.
    assertEquals(
      10.0,
      InjectionSettlement.sideEffect(10.0, InjectionSettlement.clampSpeed(7.25), 810L, 810L),
      1.0e-12
    )
  }

  @Test
  def remainderScalesDropletsAndDoseProportionally(): Unit = {
    val contents = SyringeContents(LiquidContents.RefinedPoppyExtract.liquid, 810L, 50.0)
    val half = InjectionSettlement.remainder(contents, 405L).get
    assertEquals(405L, half.droplets)
    assertEquals(25.0, half.opioidDose, 1.0e-12)

    val quarter = InjectionSettlement.remainder(contents, 202L).get
    assertEquals(608L, quarter.droplets)
    assertEquals(50.0 * 608.0 / 810.0, quarter.opioidDose, 1.0e-12)
  }

  @Test
  def emptiedRemainderIsNone(): Unit = {
    val contents = SyringeContents(LiquidContents.RefinedPoppyExtract.liquid, 810L, 50.0)
    assertTrue(InjectionSettlement.remainder(contents, 810L).isEmpty)
    assertTrue(InjectionSettlement.remainder(contents, 9999L).isEmpty)
  }

  @Test
  def multiBatchCumulativeEqualsSingleFullPush(): Unit = {
    val contents = SyringeContents(LiquidContents.RefinedPoppyExtract.liquid, 810L, 50.0)
    val batches = List(200L, 205L, 405L)

    var settledDose = 0.0
    var settledSideEffects = 0.0
    var current = contents
    batches.foreach { batch =>
      settledDose += InjectionSettlement.doseFor(current.opioidDose, batch, current.droplets)
      settledSideEffects += InjectionSettlement.sideEffect(10.0, 1.0, batch, 810L)
      current = InjectionSettlement.remainder(current, batch).getOrElse(current)
    }

    assertEquals(50.0, settledDose, 1.0e-9, "batched dose must sum to the full stored dose")
    assertEquals(10.0, settledSideEffects, 1.0e-9, "batched side effects must sum to the cap")
    assertTrue(InjectionSettlement.remainder(contents, 810L).isEmpty)
  }

  @Test
  def doseForIsProportionalToCurrentContents(): Unit = {
    // A resumed (already half-empty) syringe settles against its own remainder, not a full one.
    assertEquals(
      25.0 * 203.0 / 405.0,
      InjectionSettlement.doseFor(25.0, 203L, 405L),
      1.0e-12
    )
    assertEquals(25.0, InjectionSettlement.doseFor(25.0, 405L, 405L), 1.0e-12)
    assertEquals(0.0, InjectionSettlement.doseFor(25.0, 405L, 0L), 1.0e-12)
  }
}
