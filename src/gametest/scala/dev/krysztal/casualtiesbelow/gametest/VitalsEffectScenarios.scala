package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import dev.krysztal.casualtiesbelow.api.body.vitals.ConsciousnessSnapshot
import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.effect.CasualtiesBelowEffects
import dev.krysztal.casualtiesbelow.internal.extension.PlayerExtensions.*

object VitalsEffectScenarios {

  def mirrorsVitalsToStatusEffects(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val vitals = player.vitals
    val maxBloodVolume = CasualtiesBelowConfig.vitals.maxBloodVolume.get()
    val bloodLossStart = CasualtiesBelowConfig.vitals.bloodDesaturationStartFraction.get()
    val hypoxiaThreshold = CasualtiesBelowConfig.vitals.bloodOxygenHypoxiaThreshold.get()
    val coldThreshold = CasualtiesBelowConfig.temperature.penaltyBandLowCelsius.get()
    val hotThreshold = CasualtiesBelowConfig.temperature.penaltyBandHighCelsius.get()

    VitalsMutations.setOpioidLevel(vitals, 40.0)
    VitalsMutations.setOpioidDependence(vitals, 40.0)
    VitalsMutations.setBloodOxygen(vitals, hypoxiaThreshold - 0.1)
    VitalsMutations.setBloodVolume(vitals, maxBloodVolume * bloodLossStart - 1.0)
    VitalsMutations.setSepsis(vitals, 1.0)
    VitalsMutations.setBodyTemperature(vitals, coldThreshold - 0.1)
    VitalsMutations.applyConsciousnessState(
      vitals,
      ConsciousnessSnapshot(0.0, unconscious = true)
    )
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
    helper.assertTrue(
      Option(player.getEffect(CasualtiesBelowEffects.Hypoxia)).exists(_.getAmplifier == 0),
      "severe low blood oxygen should produce a hypoxia cue"
    )
    helper.assertTrue(
      Option(player.getEffect(CasualtiesBelowEffects.BloodLoss)).exists(_.getAmplifier == 0),
      "blood volume below the desaturation onset should produce a blood-loss cue"
    )
    helper.assertTrue(
      Option(player.getEffect(CasualtiesBelowEffects.Sepsis)).exists(_.getAmplifier == 0),
      "positive sepsis should produce a sepsis cue"
    )
    helper.assertTrue(
      Option(player.getEffect(CasualtiesBelowEffects.Hypothermia)).exists(_.getAmplifier == 0),
      "temperature below the penalty band should produce a hypothermia cue"
    )
    helper.assertTrue(
      Option(player.getEffect(CasualtiesBelowEffects.Unconsciousness)).exists(
        _.getAmplifier == 0
      ),
      "the unconscious latch should produce an unconsciousness cue"
    )

    VitalsMutations.setBodyTemperature(vitals, hotThreshold + 0.1)
    CasualtiesBelowEffects.synchronizeFromVitals(player)
    helper.assertTrue(
      Option(player.getEffect(CasualtiesBelowEffects.Hypothermia)).isEmpty &&
        Option(player.getEffect(CasualtiesBelowEffects.Hyperthermia)).exists(_.getAmplifier == 0),
      "temperature above the penalty band should replace hypothermia with hyperthermia"
    )

    VitalsMutations.setOpioidLevel(vitals, 0.0)
    VitalsMutations.setOpioidDependence(vitals, 0.0)
    VitalsMutations.setBloodOxygen(vitals, VitalsComponent.MaxBloodOxygen)
    VitalsMutations.setBloodVolume(vitals, maxBloodVolume)
    VitalsMutations.setSepsis(vitals, 0.0)
    VitalsMutations.setBodyTemperature(vitals, VitalsComponent.NormalBodyTemperature)
    VitalsMutations.applyConsciousnessState(
      vitals,
      ConsciousnessSnapshot(VitalsComponent.MaxValue, unconscious = false)
    )
    CasualtiesBelowEffects.synchronizeFromVitals(player)
    helper.assertTrue(
      List(
        CasualtiesBelowEffects.OpioidAnalgesia,
        CasualtiesBelowEffects.OpioidDependence,
        CasualtiesBelowEffects.Hypoxia,
        CasualtiesBelowEffects.BloodLoss,
        CasualtiesBelowEffects.Sepsis,
        CasualtiesBelowEffects.Hypothermia,
        CasualtiesBelowEffects.Hyperthermia,
        CasualtiesBelowEffects.Unconsciousness
      ).forall(holder => Option(player.getEffect(holder)).isEmpty),
      "derived effects should be removed when their source vitals recover"
    )
    helper.succeed()
  }
}
