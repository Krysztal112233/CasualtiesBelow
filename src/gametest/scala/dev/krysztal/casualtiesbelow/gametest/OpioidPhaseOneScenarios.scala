package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowDamageTypes
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.component.ComponentAccess
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.damage.LimbInjuryService
import dev.krysztal.casualtiesbelow.physiology.pain.PainShock
import dev.krysztal.casualtiesbelow.physiology.progression.InjuryProgression

/** Scala scenario implementations invoked by the Java GameTest discovery bridge. */
object OpioidPhaseOneScenarios {

  def analgesiaShockSuppression(helper: GameTestHelper): Unit = {
    val player = survivalPlayer(helper)
    val vitals = ComponentAccess.vitals(player)
    BodyMutations.mutate(player, BodyPart.Torso) { limb => limb.pain = 100.0 }

    helper
      .startSequence()
      .thenExecuteFor(20, () => InjuryProgression.tickForGameTest(player))
      .thenExecute(() =>
        helper.assertTrue(
          CasualtiesBelowComponents.vitals(player).shock.load > 0.0,
          "Control pain did not accumulate shock load"
        )
      )
      .thenExecute(() => {
        PainShock.applyAuthoritativeEdit(player, vitals, 0.0)
        VitalsMutations.setOpioidLevel(vitals, 100.0)
      })
      .thenExecuteFor(
        200,
        () => {
          InjuryProgression.tickForGameTest(player)
          val observedVitals = CasualtiesBelowComponents.vitals(player)
          val rawPain = CasualtiesBelowComponents.body(player).stats(BodyPart.Torso).pain
          helper.assertTrue(rawPain > 90.0, "Raw limb pain did not remain severe")
          helper.assertTrue(
            observedVitals.shock.load <= 1.0e-9,
            "Opioid analgesia allowed shock load to accumulate"
          )
        }
      )
      .thenSucceed()
  }

  def overdoseDeath(helper: GameTestHelper): Unit = {
    val player = survivalPlayer(helper)
    VitalsMutations.setOpioidLevel(ComponentAccess.vitals(player), 200.0)

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
    val vitals = ComponentAccess.vitals(player)
    player.getFoodData.setFoodLevel(20)
    VitalsMutations.setImmuneHealth(vitals, 100.0)
    VitalsMutations.setOpioidDependence(vitals, 50.0)
    VitalsMutations.setOpioidLevel(vitals, 0.0)

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
      .thenExecuteFor(100, () => InjuryProgression.tickForGameTest(player))
      .thenExecute(() => {
        val observed = CasualtiesBelowComponents.vitals(player)
        helper.assertTrue(observed.discomfort > 0.0, "Withdrawal discomfort did not rise")
        helper.assertTrue(
          approximately(observed.infection.immuneHealth, 100.0),
          "Withdrawal did not cancel well-fed immune regeneration"
        )
      })
      .thenSucceed()
  }

  private def survivalPlayer(helper: GameTestHelper): ServerPlayer =
    GameTestPlayers.createSurvivalPlayer(helper)

  private def approximately(actual: Double, expected: Double): Boolean =
    math.abs(actual - expected) <= 1.0e-6
}
