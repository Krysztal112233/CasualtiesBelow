package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.ProblemReporter
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.level.storage.TagValueOutput
import net.minecraft.world.level.storage.ValueInput

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.internal.extension.Prelude.*
import dev.krysztal.casualtiesbelow.physiology.adrenaline.AdrenalineState
import dev.krysztal.casualtiesbelow.physiology.opioid.OpioidState

import org.ladysnake.cca.api.v8.component.CardinalComponent

/** Persistence round-trip pins: whatever physiology writes must survive a writeData/readData cycle.
  * These are the only tests guarding the NBT layer, so they cover every scalar the components
  * persist (coherent values only — subsystem normalize rules are pinned by the unit tests, not
  * here).
  */
object PersistenceScenarios {

  def vitalsRoundTripPreservesState(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val vitals = player.vitals

    vitals.setImmuneHealth(137.5)
    vitals.setBloodOxygen(40.0)
    vitals.setBloodVolume(500.0)
    vitals.applyHypoxiaExposureTicks(120)
    vitals.applyTotemHemostasisTicks(60)
    vitals.applyAdrenalineState(AdrenalineState(40.0, 0))
    vitals.applyOpioidState(OpioidState(3.0, 7.0))
    vitals.setSepsis(12.5)
    vitals.setDiscomfort(42.0)
    vitals.setDirtiness(66.0)
    vitals.setBodyTemperature(38.5)
    vitals.setWetness(0.75)

    val saved = write(helper, vitals)

    // Scramble the live state; the read must restore every field from the tag alone.
    vitals.setImmuneHealth(1.0)
    vitals.setBloodVolume(1.0)
    vitals.setDiscomfort(0.0)
    vitals.applyOpioidState(OpioidState(0.0, 0.0))
    vitals.readData(read(helper, saved))

    helper.assertTrue(
      vitals.infection.immuneHealth == 137.5,
      s"immune ${vitals.infection.immuneHealth}"
    )
    helper.assertTrue(
      vitals.circulation.bloodOxygen == 40.0,
      s"oxygen ${vitals.circulation.bloodOxygen}"
    )
    helper.assertTrue(
      vitals.circulation.bloodVolume == 500.0,
      s"blood ${vitals.circulation.bloodVolume}"
    )
    helper.assertTrue(
      vitals.hypoxiaExposureTicks == 120,
      s"hypoxia ${vitals.hypoxiaExposureTicks}"
    )
    helper.assertTrue(
      vitals.totemHemostasisTicks == 60,
      s"totem ${vitals.totemHemostasisTicks}"
    )
    helper.assertTrue(vitals.adrenaline == 40.0, s"adrenaline ${vitals.adrenaline}")
    helper.assertTrue(vitals.opioidLevel == 3.0, s"opioid level ${vitals.opioidLevel}")
    helper.assertTrue(
      vitals.opioidDependence == 7.0,
      s"opioid dependence ${vitals.opioidDependence}"
    )
    helper.assertTrue(vitals.infection.sepsis == 12.5, s"sepsis ${vitals.infection.sepsis}")
    helper.assertTrue(vitals.discomfort == 42.0, s"discomfort ${vitals.discomfort}")
    helper.assertTrue(vitals.dirtiness == 66.0, s"dirtiness ${vitals.dirtiness}")
    helper.assertTrue(vitals.bodyTemperature == 38.5, s"temperature ${vitals.bodyTemperature}")
    helper.assertTrue(vitals.wetness == 0.75, s"wetness ${vitals.wetness}")
    helper.succeed()
  }

  def bodyRoundTripPreservesLimbs(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    BodyMutations.mutate(player, BodyPart.ArmLeft) { state =>
      state.skinIntegrity = 40.0
      state.muscleHealth = 55.0
      state.externalBleedingRate = 0.5
      state.pain = 30.0
      state.fractureRecoveryTicks = Some(123)
      state.infectionProgress = Some(12.5)
    }
    BodyMutations.mutate(player, BodyPart.LegRight) { state =>
      state.dislocated = true
      state.pain = 10.0
    }

    val body = CasualtiesBelowComponents.Body.get(player)
    val saved = write(helper, body)
    BodyMutations.reset(player)
    body.readData(read(helper, saved))

    val after = CasualtiesBelowComponents.body(player)
    val arm = after.stats(BodyPart.ArmLeft)
    helper.assertTrue(
      arm.skinIntegrity == 40.0 && arm.muscleHealth == 55.0,
      s"arm skin=${arm.skinIntegrity} muscle=${arm.muscleHealth}"
    )
    helper.assertTrue(
      arm.externalBleedingRate == 0.5 && arm.pain == 30.0,
      s"arm bleeding=${arm.externalBleedingRate} pain=${arm.pain}"
    )
    helper.assertTrue(
      arm.fractureRecoveryTicks.orElse(-1) == 123,
      s"arm fracture ${arm.fractureRecoveryTicks}"
    )
    helper.assertTrue(
      arm.infectionProgress.orElse(-1.0) == 12.5,
      s"arm infection ${arm.infectionProgress}"
    )
    val leg = after.stats(BodyPart.LegRight)
    helper.assertTrue(
      leg.dislocated && leg.pain == 10.0,
      s"leg dislocated=${leg.dislocated} pain=${leg.pain}"
    )
    val head = after.stats(BodyPart.Head)
    helper.assertTrue(
      head.skinIntegrity == 100.0 && head.infectionProgress.isEmpty,
      "untouched limbs must read back as pristine"
    )
    helper.succeed()
  }

  private def write(helper: GameTestHelper, component: CardinalComponent): CompoundTag = {
    val reporter = new ProblemReporter.ScopedCollector(CasualtiesBelow.Logger)
    try {
      val out = TagValueOutput.createWithContext(reporter, helper.getLevel.registryAccess())
      component.writeData(out)
      out.buildResult()
    } finally {
      reporter.close()
    }
  }

  private def read(helper: GameTestHelper, tag: CompoundTag): ValueInput = {
    TagValueInput.create(
      new ProblemReporter.ScopedCollector(CasualtiesBelow.Logger),
      helper.getLevel.registryAccess(),
      tag
    )
  }
}
