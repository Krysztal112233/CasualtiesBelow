package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*
import dev.krysztal.casualtiesbelow.physiology.dirtiness.Dirtiness
import dev.krysztal.casualtiesbelow.physiology.progression.InjuryProgression
import dev.krysztal.casualtiesbelow.physiology.temperature.Temperature

/** In-game validation of sweating and its hygiene coupling (see the 下游对接 design doc): a hot core
  * adds wetness on the sweat axis, and sweat accelerates passive dirtiness accrual.
  *
  * Core temperature is pinned above the sweat threshold, so the environment plays no part; all
  * expectations are computed from the live config, not hardcoded.
  */
object SweatScenarios {

  /** A hot core produces sweat: wetness climbs without any water source or exertion. */
  def hotExertionProducesSweat(helper: GameTestHelper): Unit = {
    helper.getLevel.setRainLevel(0.0f)
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val vitals = player.vitals
    vitals.setWetness(0.0)
    // Well above the sweat gate so sweat dominates the drying terms; penalty-band side effects at
    // 40°C do not touch the wetness axis.
    val pinnedCore = Consts.Wetness.SweatCoreTempThreshold + 2.0

    val before = vitals.wetness
    (1 to 200).foreach { _ =>
      vitals.setBodyTemperature(pinnedCore)
      InjuryProgression.tickForGameTest(player)
    }
    // Net wetness rate is the sweat rate (2.0/s at the pinned core) minus the drying terms; under
    // the default config sweat dominates, so a fixed threshold is robust — world preset changes
    // only move the drying term.
    helper.assertTrue(
      vitals.wetness > before + 5.0,
      s"a hot core must sweat (wetness rose to ${vitals.wetness} from $before after 10 s)"
    )
    helper.succeed()
  }

  /** A cool core produces no sweat even under sustained exertion: temperature, not exertion, gates
    * sweating.
    */
  def coolCoreSuppressesSweat(helper: GameTestHelper): Unit = {
    helper.getLevel.setRainLevel(0.0f)
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val vitals = player.vitals
    vitals.setWetness(0.0)
    val pinnedCore = Consts.Wetness.SweatCoreTempThreshold - 1.5

    (1 to 200).foreach { _ =>
      player.getFoodData.addExhaustion(0.09f)
      vitals.setBodyTemperature(pinnedCore)
      InjuryProgression.tickForGameTest(player)
    }
    helper.assertTrue(
      vitals.wetness < 1.0,
      s"a cool core must not sweat despite exertion (wetness ${vitals.wetness})"
    )
    helper.succeed()
  }

  /** Sweat marks accelerate passive dirtiness accrual against a non-sweating control. */
  def sweatingAccumulatesDirtinessFaster(helper: GameTestHelper): Unit = {
    helper.getLevel.setRainLevel(0.0f)
    val beforeDirtiness = {
      val control = GameTestPlayers.createSurvivalPlayer(helper)
      val controlVitals = control.vitals
      val start = controlVitals.dirtiness
      (1 to 1200).foreach(_ => Dirtiness.tickForGameTest(control))
      controlVitals.dirtiness - start
    }

    val sweating = GameTestPlayers.createSurvivalPlayer(helper)
    val vitals = sweating.vitals
    val start = vitals.dirtiness
    (1 to 1200).foreach { _ =>
      Dirtiness.markSweating(sweating.getUUID)
      Dirtiness.tickForGameTest(sweating)
    }
    val sweatingGain = vitals.dirtiness - start
    helper.assertTrue(
      sweatingGain > beforeDirtiness + 0.01,
      s"sweating must accumulate dirtiness faster (sweating $sweatingGain vs control $beforeDirtiness)"
    )
    helper.succeed()
  }
}
