package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import dev.krysztal.casualtiesbelow.effect.CasualtiesBelowPotionEffects

/** In-game validation of the totem rescue grants: the skin/muscle recovery burst must apply at the
  * hardcoded amplifier (2 = level III) and duration (20 seconds).
  */
object TotemRecoveryScenarios {

  def totemGrantsRecoveryBurst(helper: GameTestHelper): Unit = {
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
      val instance = player.getEffect(holder)
      helper.assertTrue(
        instance.getAmplifier == 2,
        s"totem recovery burst must be level III: ${instance.getAmplifier}"
      )
      helper.assertTrue(
        instance.getDuration == 400,
        s"totem recovery burst must last 20 seconds: ${instance.getDuration}"
      )
    }
    helper.succeed()
  }
}
