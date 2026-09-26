package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.api.body.vitals.ConsciousnessSnapshot
import dev.krysztal.casualtiesbelow.api.body.vitals.PainShockStage
import dev.krysztal.casualtiesbelow.api.body.vitals.ShockSnapshot
import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.effect.CasualtiesBelowEffects
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extension.Prelude.*
import dev.krysztal.casualtiesbelow.physiology.adrenaline.AdrenalineState

object VitalsEffectScenarios {

  def mirrorsVitalsToStatusEffects(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val vitals = player.vitals
    val maxBloodVolume = Consts.Vitals.MaxBloodVolume
    val hypoxiaThreshold = Consts.Vitals.BloodOxygenHypoxiaThreshold
    val coldThreshold = Consts.Temperature.PenaltyBandLowCelsius
    val hotThreshold = Consts.Temperature.PenaltyBandHighCelsius
    val grimeThreshold = Consts.Visuals.DirtinessBandGrimy

    vitals.setOpioidLevel(40.0)
    vitals.setOpioidDependence(40.0)
    vitals.setBloodOxygen(hypoxiaThreshold - 0.1)
    vitals.setBloodVolume(maxBloodVolume * 0.9)
    BodyMutations.mutate(player, BodyPart.Head, markDirty = false) { state =>
      state.externalBleedingRate = 0.4
    }
    vitals.setSepsis(1.0)
    vitals.setBodyTemperature(coldThreshold - 0.1)
    vitals.applyConsciousnessState(ConsciousnessSnapshot(0.0, unconscious = true))
    vitals.applyShockState(ShockSnapshot(50.0, PainShockStage.Collapsed))
    vitals.applyAdrenalineState(AdrenalineState(1.0, 20))
    vitals.setWetness(0.01)
    vitals.setDirtiness(grimeThreshold)
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
      Option(player.getEffect(CasualtiesBelowEffects.Hypovolemia)).exists(_.getAmplifier == 0),
      "10% blood-volume loss should produce a hypovolemia I cue"
    )
    helper.assertTrue(
      Option(player.getEffect(CasualtiesBelowEffects.BloodLoss)).exists(_.getAmplifier == 0),
      "an 8–15 minute full-volume bleed estimate should produce a blood-loss I cue"
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
    helper.assertTrue(
      Option(player.getEffect(CasualtiesBelowEffects.PainShock)).exists(_.getAmplifier == 0),
      "an active shock episode should produce a shock cue"
    )
    helper.assertTrue(
      Option(player.getEffect(CasualtiesBelowEffects.Alertness)).exists(_.getAmplifier == 0),
      "positive adrenaline should produce an alertness cue"
    )
    helper.assertTrue(
      Option(player.getEffect(CasualtiesBelowEffects.Wetness)).exists(_.getAmplifier == 0),
      "positive wetness should produce a wetness cue"
    )
    helper.assertTrue(
      Option(player.getEffect(CasualtiesBelowEffects.Dirtiness)).exists(_.getAmplifier == 0),
      "dirtiness at the grimy band should produce a dirtiness cue"
    )

    vitals.setBodyTemperature(hotThreshold + 0.1)
    CasualtiesBelowEffects.synchronizeFromVitals(player)
    helper.assertTrue(
      Option(player.getEffect(CasualtiesBelowEffects.Hypothermia)).isEmpty &&
        Option(player.getEffect(CasualtiesBelowEffects.Hyperthermia)).exists(_.getAmplifier == 0),
      "temperature above the penalty band should replace hypothermia with hyperthermia"
    )

    vitals.setOpioidLevel(0.0)
    vitals.setOpioidDependence(0.0)
    vitals.setBloodOxygen(VitalsComponent.MaxBloodOxygen)
    vitals.setBloodVolume(maxBloodVolume)
    BodyMutations.mutate(player, BodyPart.Head, markDirty = false) { state =>
      state.externalBleedingRate = 0.0
    }
    vitals.setSepsis(0.0)
    vitals.setBodyTemperature(VitalsComponent.NormalBodyTemperature)
    vitals.applyConsciousnessState(
      ConsciousnessSnapshot(VitalsComponent.MaxValue, unconscious = false)
    )
    vitals.applyShockState(ShockSnapshot(0.0, PainShockStage.Stable))
    vitals.applyAdrenalineState(AdrenalineState.Empty)
    vitals.setWetness(0.0)
    vitals.setDirtiness(0.0)
    CasualtiesBelowEffects.synchronizeFromVitals(player)
    helper.assertTrue(
      List(
        CasualtiesBelowEffects.OpioidAnalgesia,
        CasualtiesBelowEffects.OpioidDependence,
        CasualtiesBelowEffects.Hypoxia,
        CasualtiesBelowEffects.Hypovolemia,
        CasualtiesBelowEffects.BloodLoss,
        CasualtiesBelowEffects.Sepsis,
        CasualtiesBelowEffects.Hypothermia,
        CasualtiesBelowEffects.Hyperthermia,
        CasualtiesBelowEffects.Unconsciousness,
        CasualtiesBelowEffects.PainShock,
        CasualtiesBelowEffects.Alertness,
        CasualtiesBelowEffects.Wetness,
        CasualtiesBelowEffects.Dirtiness
      ).forall(holder => Option(player.getEffect(holder)).isEmpty),
      "derived effects should be removed when their source vitals recover"
    )
    helper.succeed()
  }
}
