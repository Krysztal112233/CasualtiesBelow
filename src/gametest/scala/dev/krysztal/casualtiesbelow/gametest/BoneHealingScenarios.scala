package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.effect.CasualtiesBelowPotionEffects
import dev.krysztal.casualtiesbelow.internal.Consts

/** In-game validation of the standalone Bone Healing effect: direct per-tick application advances
  * fracture recovery countdowns at the configured rate, heals completed fractures, and spreads over
  * randomly picked limbs.
  */
object BoneHealingScenarios {

  def effectAdvancesAndCompletesFractureRecovery(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val part = BodyPart.ArmLeft
    // Initial countdown sized for the configured rate: the first 100 ticks must leave a remainder
    // (midway assertion), and the second 100 ticks must finish the heal.
    BodyMutations.mutate(player, part) { state =>
      state.fractureRecoveryTicks = Some(400)
    }

    val effect = CasualtiesBelowPotionEffects.BoneHealing.value()
    val level = helper.getLevel
    (1 to 100).foreach { _ =>
      effect.applyEffectTick(level, player, 0)
    }

    val expectedRemaining = 400 - 100 * Consts.Regeneration.BoneHealingEffectPerTick
    val midway = CasualtiesBelowComponents.body(player).stats(part)
    helper.assertTrue(
      midway.fractureRecoveryTicks.orElse(-1) == expectedRemaining.toInt,
      s"bone healing must advance the countdown per tick: expected $expectedRemaining, " +
        s"got ${midway.fractureRecoveryTicks}"
    )

    (1 to 100).foreach { _ =>
      effect.applyEffectTick(level, player, 0)
    }
    val healed = CasualtiesBelowComponents.body(player).stats(part)
    helper.assertTrue(
      healed.fractureRecoveryTicks.isEmpty,
      s"bone healing must complete the fracture once the countdown expires: " +
        s"got ${healed.fractureRecoveryTicks}"
    )
    helper.succeed()
  }

  /** With several fractured limbs the effect picks one at random each tick: the total countdown
    * reduction is conserved regardless of the random choices, and no countdown ever grows.
    */
  def effectDistributesAcrossFracturedLimbs(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    Seq(BodyPart.ArmLeft, BodyPart.LegRight).foreach { part =>
      BodyMutations.mutate(player, part) { state =>
        state.fractureRecoveryTicks = Some(200)
      }
    }

    val effect = CasualtiesBelowPotionEffects.BoneHealing.value()
    val level = helper.getLevel
    val ticks = 100
    (1 to ticks).foreach { _ =>
      effect.applyEffectTick(level, player, 0)
    }

    val body = CasualtiesBelowComponents.body(player)
    val arm = body.stats(BodyPart.ArmLeft).fractureRecoveryTicks.orElse(0)
    val leg = body.stats(BodyPart.LegRight).fractureRecoveryTicks.orElse(0)
    val expectedTotal = 400 - ticks * Consts.Regeneration.BoneHealingEffectPerTick
    helper.assertTrue(
      math.abs(arm + leg - expectedTotal) < 1.0e-9,
      s"advanced countdown must be conserved across random picks: expected total " +
        s"$expectedTotal, got ${arm + leg}"
    )
    helper.assertTrue(
      arm <= 200 && leg <= 200,
      s"no countdown may grow from the effect: arm=$arm leg=$leg"
    )
    helper.succeed()
  }
}
