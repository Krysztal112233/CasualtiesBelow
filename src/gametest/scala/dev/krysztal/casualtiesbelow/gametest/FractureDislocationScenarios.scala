package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.effect.CasualtiesBelowEffects

/** Behavioral validation of the fracture/dislocation mirror effects: the vitals synchronizer must
  * show the display effect while the injury is present and retract it once the injury heals.
  */
object FractureDislocationScenarios {

  def fractureMirrorFollowsLimbState(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)

    BodyMutations.mutate(player, BodyPart.ArmLeft) { state =>
      state.fractureRecoveryTicks = Some(1000)
    }
    CasualtiesBelowEffects.synchronizeFromVitals(player)
    helper.assertTrue(
      player.hasEffect(CasualtiesBelowEffects.Fracture),
      "fracture mirror must appear once a limb is fractured"
    )

    BodyMutations.mutate(player, BodyPart.ArmLeft) { state =>
      state.fractureRecoveryTicks = None
    }
    CasualtiesBelowEffects.synchronizeFromVitals(player)
    helper.assertTrue(
      !player.hasEffect(CasualtiesBelowEffects.Fracture),
      "fracture mirror must disappear once no limb is fractured"
    )
    helper.succeed()
  }

  def dislocationMirrorFollowsLimbState(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)

    BodyMutations.mutate(player, BodyPart.ArmLeft) { state =>
      state.dislocated = true
    }
    CasualtiesBelowEffects.synchronizeFromVitals(player)
    helper.assertTrue(
      player.hasEffect(CasualtiesBelowEffects.Dislocation),
      "dislocation mirror must appear once a limb is dislocated"
    )

    BodyMutations.mutate(player, BodyPart.ArmLeft) { state =>
      state.dislocated = false
    }
    CasualtiesBelowEffects.synchronizeFromVitals(player)
    helper.assertTrue(
      !player.hasEffect(CasualtiesBelowEffects.Dislocation),
      "dislocation mirror must disappear once no limb is dislocated"
    )
    helper.succeed()
  }
}
