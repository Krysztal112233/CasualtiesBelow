package dev.krysztal.casualtiesbelow.physiology.limb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Pure-math coverage for [[LimbCalc]]: infection progression, pain grants, and strain rates. */
class LimbCalcTest {

  // ---- nextInfectionProgress ----

  @Test
  def infectionSpreadsWhenImmuneIsWeakAndRecedesWhenStrong(): Unit = {
    assertEquals(
      12.0,
      LimbCalc.nextInfectionProgress(10.0, 2.0, 1.0, 0.0, 1.0, 100.0),
      1.0e-9,
      "zero immune: full spread, no fight"
    )
    assertEquals(
      9.0,
      LimbCalc.nextInfectionProgress(10.0, 2.0, 1.0, 1.0, 1.0, 100.0),
      1.0e-9,
      "full immune: no spread, full fight"
    )
    assertEquals(
      10.0,
      LimbCalc.nextInfectionProgress(10.0, 2.0, 2.0, 0.5, 1.0, 100.0),
      1.0e-9,
      "break-even: spread equals fight"
    )
  }

  @Test
  def infectionProgressClampsAtMaxAndMayCrossZero(): Unit = {
    assertEquals(
      100.0,
      LimbCalc.nextInfectionProgress(99.5, 2.0, 0.0, 0.0, 1.0, 100.0),
      1.0e-9,
      "spread clamps at the limb maximum"
    )
    assertEquals(
      -0.5,
      LimbCalc.nextInfectionProgress(0.0, 0.0, 1.0, 1.0, 0.5, 100.0),
      1.0e-9,
      "fight can push progress negative, which the caller treats as cleared"
    )
  }

  // ---- infectionPainAfterGrant ----

  @Test
  def withdrawalHyperalgesiaScalesInfectionPainGrant(): Unit = {
    assertEquals(10.125, LimbCalc.infectionPainAfterGrant(10.0, 0.1, 1.25, 100.0), 1.0e-9)
    assertEquals(10.1, LimbCalc.infectionPainAfterGrant(10.0, 0.1, 1.0, 100.0), 1.0e-9)
  }

  @Test
  def infectionPainGrantIgnoresNegativesAndClamps(): Unit = {
    assertEquals(
      10.0,
      LimbCalc.infectionPainAfterGrant(10.0, -5.0, 1.25, 100.0),
      1.0e-9,
      "negative grants never reduce pain"
    )
    assertEquals(
      10.0,
      LimbCalc.infectionPainAfterGrant(10.0, 0.1, -1.0, 100.0),
      1.0e-9,
      "negative multipliers never reduce pain"
    )
    assertEquals(
      100.0,
      LimbCalc.infectionPainAfterGrant(99.9, 1.0, 1.0, 100.0),
      1.0e-9,
      "pain clamps at the limb maximum"
    )
  }

  // ---- immuneScaledMultiplier ----

  @Test
  def immuneScaledMultiplierInterpolatesMinToFull(): Unit = {
    assertEquals(0.25, LimbCalc.immuneScaledMultiplier(0.25, 0.0), 1.0e-12, "zero immune: minimum")
    assertEquals(1.0, LimbCalc.immuneScaledMultiplier(0.25, 1.0), 1.0e-12, "full immune: full rate")
    assertEquals(
      0.625,
      LimbCalc.immuneScaledMultiplier(0.25, 0.5),
      1.0e-12,
      "half immune: midpoint"
    )
  }

  // ---- tissueDamageFraction ----

  @Test
  def tissueDamageFractionAveragesMissingMuscleAndSkin(): Unit = {
    assertEquals(0.0, LimbCalc.tissueDamageFraction(100.0, 100.0, 100.0), 1.0e-12, "intact limb")
    assertEquals(0.5, LimbCalc.tissueDamageFraction(50.0, 50.0, 100.0), 1.0e-12, "half-gone limb")
    assertEquals(1.0, LimbCalc.tissueDamageFraction(0.0, 0.0, 100.0), 1.0e-12, "destroyed limb")
  }

  // ---- walkingStrainRate ----

  @Test
  def walkingStrainStacksFractureAndDislocation(): Unit = {
    assertEquals(
      0.0,
      LimbCalc.walkingStrainRate(false, true, true, 0.03, 0.02),
      1.0e-12,
      "not bearing weight: no strain at all"
    )
    assertEquals(0.03, LimbCalc.walkingStrainRate(true, true, false, 0.03, 0.02), 1.0e-12)
    assertEquals(0.02, LimbCalc.walkingStrainRate(true, false, true, 0.03, 0.02), 1.0e-12)
    assertEquals(
      0.05,
      LimbCalc.walkingStrainRate(true, true, true, 0.03, 0.02),
      1.0e-12,
      "fracture and dislocation rates stack"
    )
  }
}
