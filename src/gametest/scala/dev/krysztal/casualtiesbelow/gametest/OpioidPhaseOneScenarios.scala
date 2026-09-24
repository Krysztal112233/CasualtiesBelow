package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowDamageTypes
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.damage.LimbInjuryService
import dev.krysztal.casualtiesbelow.internal.extension.PlayerExtensions.*
import dev.krysztal.casualtiesbelow.physiology.progression.InjuryProgression

/** Scala scenario implementations invoked by the Java GameTest discovery bridge. */
object OpioidPhaseOneScenarios {

  def opioidAcceleratesPainDecay(helper: GameTestHelper): Unit = {
    val player = survivalPlayer(helper)
    val vitals = player.vitals
    BodyMutations.mutate(player, BodyPart.Torso) { limb => limb.pain = 100.0 }

    helper
      .startSequence()
      // Control: with no opioid, only natural decay applies (0.025/tick by default), so 100 ticks
      // leave the torso at ~97.5.
      .thenExecuteFor(100, () => InjuryProgression.tickForGameTest(player))
      .thenExecute(() => {
        val naturalOnly = CasualtiesBelowComponents.body(player).stats(BodyPart.Torso).pain
        helper.assertTrue(
          naturalOnly > 97.0 && naturalOnly < 98.0,
          s"Control pain did not follow natural decay alone: $naturalOnly"
        )
        // Effective opioid = 100 / (1 + 0 / 100) = 100, adding a 0.05/tick drain on top of the
        // natural 0.025: after 100 more ticks the torso sits at ~90.0.
        VitalsMutations.setOpioidLevel(vitals, 100.0)
      })
      .thenExecuteFor(100, () => InjuryProgression.tickForGameTest(player))
      .thenExecute(() => {
        val drained = CasualtiesBelowComponents.body(player).stats(BodyPart.Torso).pain
        helper.assertTrue(
          drained > 89.0 && drained < 91.0,
          s"Opioid drain did not accelerate limb pain decay as configured: $drained"
        )
      })
      .thenSucceed()
  }

  def overdoseDeath(helper: GameTestHelper): Unit = {
    val player = survivalPlayer(helper)
    VitalsMutations.setOpioidLevel(player.vitals, 200.0)

    helper.succeedWhen(() => {
      InjuryProgression.tickForGameTest(player)
      helper.assertTrue(player.isDeadOrDying, "Opioid overdose has not caused hypoxia death yet")
      helper.assertTrue(
        Option(player.getLastDamageSource).exists(_.is(CasualtiesBelowDamageTypes.Hypoxia)),
        "Opioid overdose death did not use the hypoxia damage type"
      )
    })
  }

  def withdrawalTrio(helper: GameTestHelper): Unit = {
    val player = survivalPlayer(helper)
    val vitals = player.vitals
    player.getFoodData.setFoodLevel(20)
    VitalsMutations.setImmuneHealth(vitals, 100.0)
    VitalsMutations.setOpioidDependence(vitals, 50.0)
    VitalsMutations.setOpioidLevel(vitals, 0.0)

    // Positive control: equally fed, no dependence — its immune health must regenerate,
    // proving the harness can observe the regeneration that withdrawal cancels.
    val control = survivalPlayer(helper)
    val controlVitals = control.vitals
    control.getFoodData.setFoodLevel(20)
    VitalsMutations.setImmuneHealth(controlVitals, 100.0)

    val applied = LimbInjuryService.apply(
      player,
      BodyPart.ArmLeft,
      player.damageSources().generic(),
      damage = 0.0,
      pain = 10.0,
      jitter = 0.0
    )((_, _) => ())
    helper.assertTrue(applied, "Known withdrawal pain grant was rejected")
    helper.assertTrue(
      approximately(CasualtiesBelowComponents.body(player).stats(BodyPart.ArmLeft).pain, 12.5),
      "Withdrawal did not amplify the known pain grant by 1.25x"
    )

    helper
      .startSequence()
      .thenExecuteFor(
        100,
        () => {
          InjuryProgression.tickForGameTest(player)
          InjuryProgression.tickForGameTest(control)
        }
      )
      .thenExecute(() => {
        val observed = CasualtiesBelowComponents.vitals(player)
        helper.assertTrue(observed.discomfort > 0.0, "Withdrawal discomfort did not rise")
        helper.assertTrue(
          approximately(observed.infection.immuneHealth, 100.0),
          "Withdrawal did not cancel well-fed immune regeneration"
        )
        helper.assertTrue(
          CasualtiesBelowComponents.vitals(control).infection.immuneHealth > 100.0,
          "Control immune regeneration was not observable"
        )
      })
      .thenSucceed()
  }

  private def survivalPlayer(helper: GameTestHelper): ServerPlayer =
    GameTestPlayers.createSurvivalPlayer(helper)

  private def approximately(actual: Double, expected: Double): Boolean =
    math.abs(actual - expected) <= 1.0e-6
}
