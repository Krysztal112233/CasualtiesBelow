package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.api.body.limb.LimbSnapshot
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.effect.CasualtiesBelowPotionEffects

/** Behavioral validation of the standalone Skin Regeneration effect: it must heal damaged limbs
  * (whichever ones its random per-tick picks land on), never overshoot the skin cap, and stop the
  * bleeding once the skin closes. Exact rates are balance territory and deliberately unpinned.
  */
object SkinRegenerationScenarios {

  def effectRestoresDamagedSkin(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    BodyMutations.mutate(player, BodyPart.ArmLeft) { state =>
      state.skinIntegrity = 40.0
      state.externalBleedingRate = 0.2
    }
    BodyMutations.mutate(player, BodyPart.LegRight) { state =>
      state.skinIntegrity = 40.0
    }

    val effect = CasualtiesBelowPotionEffects.SkinRegeneration.value()
    val level = helper.getLevel

    (1 to 100).foreach { _ => effect.applyEffectTick(level, player, 0) }
    val midwayTotal = Seq(BodyPart.ArmLeft, BodyPart.LegRight)
      .map(part => CasualtiesBelowComponents.body(player).stats(part).skinIntegrity)
      .sum
    helper.assertTrue(
      midwayTotal > 80.0,
      s"skin regeneration must heal damaged limbs: total skin $midwayTotal"
    )

    // Generous tick budget: both limbs must fully heal no matter how the random picks land.
    (1 to 2400).foreach { _ => effect.applyEffectTick(level, player, 0) }
    val body = CasualtiesBelowComponents.body(player)
    val arm = body.stats(BodyPart.ArmLeft)
    val leg = body.stats(BodyPart.LegRight)
    helper.assertTrue(
      arm.skinIntegrity == LimbSnapshot.MaxValue && leg.skinIntegrity == LimbSnapshot.MaxValue,
      s"both limbs must fully heal without overshooting the cap: " +
        s"arm=${arm.skinIntegrity} leg=${leg.skinIntegrity}"
    )
    helper.assertTrue(
      arm.externalBleedingRate == 0.0,
      s"closing the skin must reconcile bleeding to zero: ${arm.externalBleedingRate}"
    )
    helper.succeed()
  }
}
