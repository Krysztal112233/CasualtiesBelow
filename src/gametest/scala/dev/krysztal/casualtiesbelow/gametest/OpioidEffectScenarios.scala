package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.effect.CasualtiesBelowEffects
import dev.krysztal.casualtiesbelow.internal.extension.PlayerExtensions.*

object OpioidEffectScenarios {

  def mirrorsVitalsToStatusEffects(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val vitals = player.vitals

    VitalsMutations.setOpioidLevel(vitals, 40.0)
    VitalsMutations.setOpioidDependence(vitals, 40.0)
    CasualtiesBelowEffects.synchronizeFromVitals(player)

    val analgesia = Option(player.getEffect(CasualtiesBelowEffects.OpioidAnalgesia))
    val dependence = Option(player.getEffect(CasualtiesBelowEffects.OpioidDependence))
    helper.assertTrue(
      analgesia.exists(_.getAmplifier == 0),
      s"expected analgesia I, got $analgesia"
    )
    helper.assertTrue(
      dependence.exists(_.getAmplifier == 1),
      s"expected dependence II, got $dependence"
    )
    helper.assertTrue(
      analgesia.exists(effect => effect.isInfiniteDuration && !effect.isVisible && effect.showIcon),
      "derived effects should be infinite, particleless, and retain their HUD icon"
    )

    VitalsMutations.setOpioidLevel(vitals, 0.0)
    VitalsMutations.setOpioidDependence(vitals, 0.0)
    CasualtiesBelowEffects.synchronizeFromVitals(player)
    helper.assertTrue(
      Option(player.getEffect(CasualtiesBelowEffects.OpioidAnalgesia)).isEmpty &&
        Option(player.getEffect(CasualtiesBelowEffects.OpioidDependence)).isEmpty,
      "derived effects should be removed when both source values return to zero"
    )
    helper.succeed()
  }
}
