package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.api.body.limb.LimbSnapshot
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.effect.CasualtiesBelowPotionEffects

/** Behavioral validation of the standalone Muscle Recovery effect: it must heal damaged limbs
  * (whichever ones its random per-tick picks land on) and never overshoot the health cap. Exact
  * rates are balance territory and deliberately unpinned.
  */
object MuscleRecoveryScenarios {

  def effectRestoresDamagedMuscle(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    BodyMutations.mutate(player, BodyPart.ArmLeft) { state =>
      state.muscleHealth = 40.0
    }
    BodyMutations.mutate(player, BodyPart.LegRight) { state =>
      state.muscleHealth = 40.0
    }

    val effect = CasualtiesBelowPotionEffects.MuscleRecovery.value()
    val level = helper.getLevel

    (1 to 100).foreach { _ => effect.applyEffectTick(level, player, 0) }
    val midwayTotal = Seq(BodyPart.ArmLeft, BodyPart.LegRight)
      .map(part => CasualtiesBelowComponents.body(player).stats(part).muscleHealth)
      .sum
    helper.assertTrue(
      midwayTotal > 80.0,
      s"muscle recovery must heal damaged limbs: total muscle $midwayTotal"
    )

    // Generous tick budget: both limbs must fully heal no matter how the random picks land.
    (1 to 2400).foreach { _ => effect.applyEffectTick(level, player, 0) }
    val body = CasualtiesBelowComponents.body(player)
    val arm = body.stats(BodyPart.ArmLeft).muscleHealth
    val leg = body.stats(BodyPart.LegRight).muscleHealth
    helper.assertTrue(
      arm == LimbSnapshot.MaxValue && leg == LimbSnapshot.MaxValue,
      s"both limbs must fully heal without overshooting the cap: arm=$arm leg=$leg"
    )
    helper.succeed()
  }
}
