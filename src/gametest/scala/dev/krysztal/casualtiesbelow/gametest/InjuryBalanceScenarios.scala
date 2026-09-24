package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.ai.attributes.Attributes

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.damage.FallDamageFormula
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.physiology.progression.InjuryProgression

/** Non-default values for the player-facing injury balance settings, restored after each test. */
object InjuryBalanceScenarios {

  def fallDamageSettingScalesPlayers(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val setting = CasualtiesBelowConfig.injurySurvival.fallDamageMultiplier
    val previous = setting.get()
    try {
      setting.set(1.0)
      val baseline = FallDamageFormula.calculateCustom(player, 12.0, 1.0f)
      helper.assertTrue(baseline > 0, "the default player fall curve must cause damage")

      setting.set(0.0)
      helper.assertTrue(
        FallDamageFormula.calculateCustom(player, 12.0, 1.0f) == 0,
        "zero fall multiplier must prevent custom player fall damage"
      )
      setting.set(2.0)
      val doubled = FallDamageFormula.calculateCustom(player, 12.0, 1.0f)
      helper.assertTrue(
        doubled >= 2 * baseline && doubled <= 2 * baseline + 1,
        s"doubling the fall curve must double damage before rounding: $baseline -> $doubled"
      )
    } finally {
      setting.set(previous)
    }
    helper.succeed()
  }

  def clottingAndNaturalHealingSettingsAreIndependent(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val part = BodyPart.ArmLeft
    val clotting = CasualtiesBelowConfig.injurySurvival.clottingSpeedMultiplier
    val healing = CasualtiesBelowConfig.injurySurvival.naturalHealingMultiplier
    val previousClotting = clotting.get()
    val previousHealing = healing.get()
    BodyMutations.mutate(player, part) { state =>
      state.skinIntegrity = 50.0
      state.muscleHealth = 50.0
      state.externalBleedingRate = 0.1
    }
    try {
      clotting.set(0.0)
      healing.set(0.0)
      InjuryProgression.tickForGameTest(player)
      val paused = CasualtiesBelowComponents.body(player).stats(part)
      helper.assertTrue(
        math.abs(paused.externalBleedingRate - 0.1) < 1.0e-9 &&
          paused.skinIntegrity == 50.0 && paused.muscleHealth == 50.0,
        "zero settings must pause clotting and natural tissue repair"
      )

      clotting.set(2.0)
      healing.set(0.0)
      InjuryProgression.tickForGameTest(player)
      val clotted = CasualtiesBelowComponents.body(player).stats(part)
      helper.assertTrue(
        math.abs(
          clotted.externalBleedingRate -
            (paused.externalBleedingRate - 2.0 * Consts.Bleeding.ClottingRatePerTick)
        ) < 1.0e-9 && clotted.skinIntegrity == 50.0 && clotted.muscleHealth == 50.0,
        "clotting alone must reduce bleeding without healing tissue"
      )

      clotting.set(0.0)
      healing.set(2.0)
      InjuryProgression.tickForGameTest(player)
      val bleeding = CasualtiesBelowComponents.body(player).stats(part)
      helper.assertTrue(
        math.abs(bleeding.externalBleedingRate - clotted.externalBleedingRate) < 1.0e-9 &&
          bleeding.skinIntegrity == 50.0 &&
          math.abs(bleeding.muscleHealth - (clotted.muscleHealth + 0.005)) < 1.0e-9,
        "natural healing alone must repair muscle without clotting an open wound"
      )

      BodyMutations.mutate(player, part) { state => state.externalBleedingRate = 0.0 }
      InjuryProgression.tickForGameTest(player)
      val afterSeal = CasualtiesBelowComponents.body(player).stats(part)
      helper.assertTrue(
        afterSeal.skinIntegrity > 50.0 && afterSeal.muscleHealth > bleeding.muscleHealth,
        "skin and muscle must both regrow once the wound seals"
      )
    } finally {
      clotting.set(previousClotting)
      healing.set(previousHealing)
    }
    helper.succeed()
  }

  def legPenaltySettingScalesSpeedAndJump(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val setting = CasualtiesBelowConfig.injurySurvival.legMovementPenaltyMultiplier
    val previousActive = setting.get()
    val previousRaw = setting.getRaw()
    try {
      // Restart-required config values keep a cached active value until the next restart.
      // Clearing it here simulates that boundary without changing production semantics.
      setting.set(0.0)
      setting.clearCache()
      BodyMutations.mutate(player, BodyPart.LegLeft) { state => state.dislocated = true }
      val normalSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED)
      val normalJump = player.getAttributeValue(Attributes.JUMP_STRENGTH)

      setting.set(1.0)
      setting.clearCache()
      BodyMutations.reconcileMovementModifiers(player)
      val injuredSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED)
      val injuredJump = player.getAttributeValue(Attributes.JUMP_STRENGTH)
      helper.assertTrue(
        injuredSpeed < normalSpeed && injuredJump < normalJump,
        s"injured legs must reduce speed and jump: multiplier=${setting.get()}, " +
          s"dislocated=${CasualtiesBelowComponents.body(player).stats(BodyPart.LegLeft).dislocated}, " +
          s"speed=$normalSpeed->$injuredSpeed, jump=$normalJump->$injuredJump"
      )
    } finally {
      // Restore both the effective cached value and the potentially different raw TOML value.
      setting.set(previousActive)
      setting.clearCache()
      setting.get()
      setting.set(previousRaw)
      BodyMutations.reconcileMovementModifiers(player)
    }
    helper.succeed()
  }
}
