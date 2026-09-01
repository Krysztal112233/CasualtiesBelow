package dev.krysztal.casualtiesbelow.item

import java.util.OptionalDouble
import java.util.OptionalInt

import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.api.body.limb.LimbSnapshot
import dev.krysztal.casualtiesbelow.component.MutableLimbState

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

final class BasicBandageItemTest {

  @Test
  def bleedingLimbOutranksMoreSevereTissueDamage(): Unit = {
    val stats = body(
      BodyPart.Head -> snapshot(skin = 10.0, muscle = 10.0),
      BodyPart.ArmLeft -> snapshot(skin = 99.0, bleeding = 0.01)
    )

    assertEquals(
      Some(BodyPart.ArmLeft),
      BasicBandageItem.selectTarget(stats.apply)
    )
  }

  @Test
  def largestTissueDamageWinsAmongBleedingLimbs(): Unit = {
    val stats = body(
      BodyPart.LegLeft -> snapshot(skin = 90.0, bleeding = 0.3),
      BodyPart.LegRight -> snapshot(skin = 50.0, bleeding = 0.1)
    )

    assertEquals(
      Some(BodyPart.LegRight),
      BasicBandageItem.selectTarget(stats.apply)
    )
  }

  @Test
  def bleedingRateBreaksEqualTissueDamage(): Unit = {
    val stats = body(
      BodyPart.LegLeft -> snapshot(skin = 80.0, bleeding = 0.1),
      BodyPart.LegRight -> snapshot(skin = 80.0, bleeding = 0.3)
    )

    assertEquals(
      Some(BodyPart.LegRight),
      BasicBandageItem.selectTarget(stats.apply)
    )
  }

  @Test
  def largestCombinedTissueDamageWinsWithoutBleeding(): Unit = {
    val stats = body(
      BodyPart.Torso -> snapshot(skin = 70.0),
      BodyPart.ArmRight -> snapshot(muscle = 50.0)
    )

    assertEquals(
      Some(BodyPart.ArmRight),
      BasicBandageItem.selectTarget(stats.apply)
    )
  }

  @Test
  def treatmentRestoresExactTissueAmountsAndSubtractsFixedBleedingRate(): Unit = {
    val stats = MutableLimbState(
      skinIntegrity = 85.0,
      muscleHealth = 93.0,
      externalBleedingRate = 0.15
    )

    BasicBandageItem.applyTreatment(stats)

    assertEquals(95.0, stats.skinIntegrity, 1.0e-9)
    assertEquals(98.0, stats.muscleHealth, 1.0e-9)
    assertEquals(0.1, stats.externalBleedingRate, 1.0e-9)
  }

  @Test
  def treatmentClampsTissueAtMaximum(): Unit = {
    val stats = MutableLimbState(skinIntegrity = 94.0, muscleHealth = 97.0)

    BasicBandageItem.applyTreatment(stats)

    assertEquals(100.0, stats.skinIntegrity, 1.0e-9)
    assertEquals(100.0, stats.muscleHealth, 1.0e-9)
  }

  @Test
  def treatmentClampsBleedingAtZero(): Unit = {
    val stats = MutableLimbState(externalBleedingRate = 0.05)

    BasicBandageItem.applyTreatment(stats)

    assertEquals(0.0, stats.externalBleedingRate, 1.0e-9)
  }

  @Test
  def stableBodyPartOrderBreaksOtherwiseEqualTies(): Unit = {
    val stats = body(
      BodyPart.Head -> snapshot(skin = 80.0),
      BodyPart.Torso -> snapshot(skin = 80.0)
    )

    assertEquals(
      Some(BodyPart.Head),
      BasicBandageItem.selectTarget(stats.apply)
    )
  }

  @Test
  def unrelatedConditionsDoNotMakeALimbTreatable(): Unit = {
    val stats = body(
      BodyPart.Head -> snapshot(
        fractureTicks = Some(200),
        infection = Some(40.0),
        dislocated = true,
        pain = 80.0
      )
    )

    assertTrue(BasicBandageItem.selectTarget(stats.apply).isEmpty)
  }

  @Test
  def fullyHealthyBodyHasNoTarget(): Unit = {
    assertTrue(BasicBandageItem.selectTarget(body().apply).isEmpty)
  }

  private def body(
      overrides: (BodyPart, LimbSnapshot)*
  ): Map[BodyPart, LimbSnapshot] = {
    BodyPart.values.map(_ -> snapshot()).toMap ++ overrides
  }

  private def snapshot(
      skin: Double = LimbSnapshot.MaxValue,
      muscle: Double = LimbSnapshot.MaxValue,
      fractureTicks: Option[Int] = None,
      infection: Option[Double] = None,
      dislocated: Boolean = false,
      bleeding: Double = 0.0,
      pain: Double = 0.0
  ): LimbSnapshot = {
    LimbSnapshot(
      muscle,
      skin,
      fractureTicks.fold(OptionalInt.empty())(OptionalInt.of),
      infection.fold(OptionalDouble.empty())(OptionalDouble.of),
      dislocated,
      bleeding,
      pain
    )
  }
}
