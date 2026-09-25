package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import dev.krysztal.casualtiesbelow.effect.CasualtiesBelowPotionEffects

/** Behavioral validation of the totem rescue grants: both recovery effects must be present as real,
  * finite grants. Potency numbers are balance territory and deliberately unpinned.
  */
object TotemRecoveryScenarios {

  def totemGrantsRecoveryEffects(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)

    CasualtiesBelowPotionEffects.grantTotemRecovery(player)

    Seq(
      CasualtiesBelowPotionEffects.SkinRegeneration,
      CasualtiesBelowPotionEffects.MuscleRecovery
    ).foreach { holder =>
      helper.assertTrue(
        player.hasEffect(holder),
        s"totem rescue must grant $holder"
      )
      helper.assertTrue(
        player.getEffect(holder).getDuration > 0,
        s"totem grant must be a real finite effect: ${player.getEffect(holder).getDuration}"
      )
    }
    helper.succeed()
  }
}
