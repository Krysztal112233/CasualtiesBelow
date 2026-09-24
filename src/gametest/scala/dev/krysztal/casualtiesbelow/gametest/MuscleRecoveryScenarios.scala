package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.effect.CasualtiesBelowPotionEffects
import dev.krysztal.casualtiesbelow.internal.Consts

/** In-game validation of the standalone Muscle Recovery effect: direct per-tick application repairs
  * damaged muscle at the configured rate, distributed over randomly picked limbs.
  */
object MuscleRecoveryScenarios {

  def effectRestoresMuscle(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val part = BodyPart.ArmLeft
    BodyMutations.mutate(player, part) { state =>
      state.muscleHealth = 40.0
    }

    val effect = CasualtiesBelowPotionEffects.MuscleRecovery.value()
    val level = helper.getLevel
    (1 to 100).foreach { _ =>
      effect.applyEffectTick(level, player, 0)
    }

    val expectedMuscle = 40.0 + 100 * Consts.Regeneration.MuscleRecoveryEffectPerTick
    val after = CasualtiesBelowComponents.body(player).stats(part)
    helper.assertTrue(
      math.abs(after.muscleHealth - expectedMuscle) < 1.0e-9,
      s"muscle recovery must restore at the per-tick rate: expected $expectedMuscle, " +
        s"got ${after.muscleHealth}"
    )
    helper.succeed()
  }

  /** With several damaged limbs the effect picks one at random each tick: the total restored muscle
    * is conserved regardless of the random choices, and no limb ever loses muscle.
    */
  def effectDistributesAcrossDamagedLimbs(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    Seq(BodyPart.ArmLeft, BodyPart.LegRight).foreach { part =>
      BodyMutations.mutate(player, part) { state =>
        state.muscleHealth = 40.0
      }
    }

    val effect = CasualtiesBelowPotionEffects.MuscleRecovery.value()
    val level = helper.getLevel
    val ticks = 100
    (1 to ticks).foreach { _ =>
      effect.applyEffectTick(level, player, 0)
    }

    val body = CasualtiesBelowComponents.body(player)
    val arm = body.stats(BodyPart.ArmLeft).muscleHealth
    val leg = body.stats(BodyPart.LegRight).muscleHealth
    val expectedTotal = 80.0 + ticks * Consts.Regeneration.MuscleRecoveryEffectPerTick
    helper.assertTrue(
      math.abs(arm + leg - expectedTotal) < 1.0e-9,
      s"restored muscle must be conserved across random picks: expected total $expectedTotal, " +
        s"got ${arm + leg}"
    )
    helper.assertTrue(
      arm >= 40.0 && leg >= 40.0,
      s"no limb may lose muscle to the effect: arm=$arm leg=$leg"
    )
    helper.succeed()
  }
}
