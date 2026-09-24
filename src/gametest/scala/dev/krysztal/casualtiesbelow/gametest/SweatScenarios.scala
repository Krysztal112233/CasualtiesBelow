package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extension.PlayerExtensions.*
import dev.krysztal.casualtiesbelow.physiology.dirtiness.Dirtiness
import dev.krysztal.casualtiesbelow.physiology.progression.InjuryProgression
import dev.krysztal.casualtiesbelow.physiology.temperature.Temperature

/** In-game validation of sweating and its hygiene coupling (see the 下游对接 design doc): a hot core
  * plus active exertion adds wetness on the sweat axis, and sweat accelerates passive dirtiness
  * accrual.
  *
  * Core temperature is pinned above the sweat threshold, so the environment plays no part; all
  * expectations are computed from the live config, not hardcoded.
  */
object SweatScenarios {

  /** A hot core plus sustained exertion produces sweat: wetness climbs without any water source.
    */
  def hotExertionProducesSweat(helper: GameTestHelper): Unit = {
    helper.getLevel.setRainLevel(0.0f)
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val vitals = player.vitals
    VitalsMutations.setWetness(vitals, 0.0)
    // Above the sweat gate but inside the penalty band: no consciousness pressure interferes.
    val pinnedCore = Consts.Temperature.SweatCoreTempThreshold + 0.5

    val before = vitals.wetness
    (1 to 200).foreach { _ =>
      player.getFoodData.addExhaustion(0.09f)
      VitalsMutations.setBodyTemperature(vitals, pinnedCore)
      InjuryProgression.tickForGameTest(player)
    }
    // Net wetness rate is the (saturated) sweat rate minus the drying curve; under the default
    // config they are an order of magnitude apart, so a fixed small threshold is robust — world
    // preset changes only move the tiny drying term.
    helper.assertTrue(
      vitals.wetness > before + 0.05,
      s"sustained exertion with a hot core must sweat (wetness rose to ${vitals.wetness} " +
        s"from $before after 10 s)"
    )
    helper.succeed()
  }

  /** The same exertion with a cool core produces no sweat. */
  def coolCoreSuppressesSweat(helper: GameTestHelper): Unit = {
    helper.getLevel.setRainLevel(0.0f)
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val vitals = player.vitals
    VitalsMutations.setWetness(vitals, 0.0)
    val pinnedCore = Consts.Temperature.SweatCoreTempThreshold - 1.5

    (1 to 200).foreach { _ =>
      player.getFoodData.addExhaustion(0.09f)
      VitalsMutations.setBodyTemperature(vitals, pinnedCore)
      InjuryProgression.tickForGameTest(player)
    }
    helper.assertTrue(
      vitals.wetness < 0.01,
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
