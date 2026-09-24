package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.effect.SkinRegenerationEffect
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.physiology.limb.BleedingCalc

/** In-game validation of the standalone Skin Regeneration effect: direct per-tick application
  * repairs damaged skin and reconciles the bleeding cap, even while the wound still bleeds.
  */
object SkinRegenerationScenarios {

  def effectRestoresSkinAndCapsBleeding(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val part = BodyPart.ArmLeft
    BodyMutations.mutate(player, part) { state =>
      state.skinIntegrity = 40.0
      state.externalBleedingRate = 0.2
    }

    val effect = SkinRegenerationEffect.SkinRegeneration.value()
    val level = helper.getLevel
    (1 to 100).foreach { _ =>
      effect.applyEffectTick(level, player, 0)
    }

    val expectedSkin = 40.0 + 100 * Consts.Regeneration.SkinRegenerationEffectPerTick
    val after = CasualtiesBelowComponents.body(player).stats(part)
    helper.assertTrue(
      math.abs(after.skinIntegrity - expectedSkin) < 1.0e-9,
      s"skin regeneration must restore at the per-tick rate: expected $expectedSkin, " +
        s"got ${after.skinIntegrity}"
    )
    helper.assertTrue(
      after.externalBleedingRate <= BleedingCalc.cap(after.skinIntegrity) + 1.0e-9,
      s"bleeding must be reconciled to the skin cap: ${after.externalBleedingRate} " +
        s"vs cap ${BleedingCalc.cap(after.skinIntegrity)}"
    )
    helper.succeed()
  }

  /** With several damaged limbs the effect picks one at random each tick: the total restored skin
    * is conserved regardless of the random choices, and no limb ever loses skin to the effect.
    */
  def effectDistributesAcrossDamagedLimbs(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    Seq(BodyPart.ArmLeft, BodyPart.LegRight).foreach { part =>
      BodyMutations.mutate(player, part) { state =>
        state.skinIntegrity = 40.0
      }
    }

    val effect = SkinRegenerationEffect.SkinRegeneration.value()
    val level = helper.getLevel
    val ticks = 100
    (1 to ticks).foreach { _ =>
      effect.applyEffectTick(level, player, 0)
    }

    val body = CasualtiesBelowComponents.body(player)
    val arm = body.stats(BodyPart.ArmLeft).skinIntegrity
    val leg = body.stats(BodyPart.LegRight).skinIntegrity
    val expectedTotal = 80.0 + ticks * Consts.Regeneration.SkinRegenerationEffectPerTick
    helper.assertTrue(
      math.abs(arm + leg - expectedTotal) < 1.0e-9,
      s"restored skin must be conserved across random picks: expected total $expectedTotal, " +
        s"got ${arm + leg}"
    )
    helper.assertTrue(
      arm >= 40.0 && leg >= 40.0,
      s"no limb may lose skin to the effect: arm=$arm leg=$leg"
    )
    helper.succeed()
  }
}
