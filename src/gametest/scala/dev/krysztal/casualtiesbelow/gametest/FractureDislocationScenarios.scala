package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.effect.CasualtiesBelowEffects

/** In-game validation of the fracture/dislocation mirror effects: the vitals synchronizer must
  * reflect limb state into the display effects and retract them once the injuries heal.
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
    helper.assertTrue(
      player.getEffect(CasualtiesBelowEffects.Fracture).getAmplifier == 0,
      "one fractured limb must map to amplifier I"
    )

    BodyMutations.mutate(player, BodyPart.LegRight) { state =>
      state.fractureRecoveryTicks = Some(1000)
    }
    CasualtiesBelowEffects.synchronizeFromVitals(player)
    helper.assertTrue(
      player.getEffect(CasualtiesBelowEffects.Fracture).getAmplifier == 1,
      "two fractured limbs must map to amplifier II"
    )

    Seq(BodyPart.ArmLeft, BodyPart.LegRight).foreach { part =>
      BodyMutations.mutate(player, part) { state =>
        state.fractureRecoveryTicks = None
      }
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
