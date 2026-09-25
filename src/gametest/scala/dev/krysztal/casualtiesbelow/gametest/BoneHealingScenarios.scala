package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.effect.CasualtiesBelowPotionEffects

/** Behavioral validation of the standalone Bone Healing effect: it must advance fracture countdowns
  * (never growing them) and complete the heal, whichever limbs its random per-tick picks land on.
  * Exact rates are balance territory and deliberately unpinned.
  */
object BoneHealingScenarios {

  def effectHealsFractures(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    BodyMutations.mutate(player, BodyPart.ArmLeft) { state =>
      state.fractureRecoveryTicks = Some(150)
    }
    BodyMutations.mutate(player, BodyPart.LegRight) { state =>
      state.fractureRecoveryTicks = Some(400)
    }

    val effect = CasualtiesBelowPotionEffects.BoneHealing.value()
    val level = helper.getLevel

    (1 to 100).foreach { _ => effect.applyEffectTick(level, player, 0) }
    val midwayArm = remaining(player, BodyPart.ArmLeft)
    val midwayLeg = remaining(player, BodyPart.LegRight)
    helper.assertTrue(
      midwayArm + midwayLeg < 550,
      s"bone healing must advance fracture countdowns: arm=$midwayArm leg=$midwayLeg"
    )
    helper.assertTrue(
      midwayArm <= 150 && midwayLeg <= 400,
      s"no countdown may grow: arm=$midwayArm leg=$midwayLeg"
    )

    // Generous tick budget: both fractures must heal no matter how the random picks land.
    (1 to 400).foreach { _ => effect.applyEffectTick(level, player, 0) }
    helper.assertTrue(
      remaining(player, BodyPart.ArmLeft) == 0 && remaining(player, BodyPart.LegRight) == 0,
      "both fractures must fully heal"
    )
    helper.succeed()
  }

  private def remaining(player: ServerPlayer, part: BodyPart): Int = {
    CasualtiesBelowComponents.body(player).stats(part).fractureRecoveryTicks.orElse(0)
  }
}
