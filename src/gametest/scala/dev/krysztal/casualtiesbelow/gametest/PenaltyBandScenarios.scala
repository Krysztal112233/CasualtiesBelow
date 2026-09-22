package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.extension.PlayerExtensions.*
import dev.krysztal.casualtiesbelow.physiology.progression.InjuryProgression
import dev.krysztal.casualtiesbelow.physiology.temperature.TemperatureCalc

/** In-game validation of the temperature penalty band (see the 下游对接 design doc): deviation outside
  * the band caps consciousness like low blood oxygen and drains immune health, cold side harder
  * than hot side.
  *
  * Biome effects are irrelevant here: scenarios pin the core temperature directly with
  * [[VitalsMutations.setBodyTemperature]] and read the pressure/drain computations, so every
  * expectation below is computed from the live config, not hardcoded.
  */
object PenaltyBandScenarios {

  /** Cold deviation below the band caps consciousness without knocking the player out. */
  def coldDeviationCapsConsciousness(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val vitals = player.vitals
    val bandLow = CasualtiesBelowConfig.PenaltyBandLowCelsius.get()
    val coldDev = bandLow - 33.0

    VitalsMutations.setBodyTemperature(vitals, 33.0)
    InjuryProgression.tickForGameTest(player)
    assertConsciousnessAtCeiling(
      helper,
      player,
      expectedCeiling(coldDev, 0.0),
      s"33°C (deviation $coldDev below the band) must cap consciousness"
    )
    helper.succeed()
  }

  /** A deeper cold deviation lowers the ceiling further, still without a knockout. */
  def deeperColdDeviationLowersCeiling(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val vitals = player.vitals
    val bandLow = CasualtiesBelowConfig.PenaltyBandLowCelsius.get()
    val coldDev = bandLow - 30.0

    VitalsMutations.setBodyTemperature(vitals, 30.0)
    InjuryProgression.tickForGameTest(player)
    assertConsciousnessAtCeiling(
      helper,
      player,
      expectedCeiling(coldDev, 0.0),
      s"30°C (deviation $coldDev below the band) must cap consciousness lower"
    )
    helper.succeed()
  }

  /** Inside the band nothing presses: consciousness stays at full and immune health is untouched by
    * temperature.
    */
  def insideBandIsPressureFree(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val vitals = player.vitals
    val immuneBefore = vitals.infection.immuneHealth
    // Food pinned to the middle band (neither fed nor hungry): zero food delta, so even a small
    // erroneous temperature drain cannot hide behind fed regen plus the max clamp.
    player.getFoodData.setFoodLevel(10)

    VitalsMutations.setBodyTemperature(vitals, 36.5)
    InjuryProgression.tickForGameTest(player)
    helper.assertTrue(
      vitals.consciousness.level > 99.0 && !vitals.consciousness.unconscious,
      s"inside the band consciousness must stay near full, got ${vitals.consciousness.level}"
    )
    helper.assertTrue(
      vitals.infection.immuneHealth == immuneBefore,
      s"inside the band immune health must be untouched by temperature, got " +
        s"${vitals.infection.immuneHealth} vs $immuneBefore"
    )
    helper.succeed()
  }

  /** Equal cold and hot deviations drain immune health, the cold side harder (the one deliberate
    * asymmetry of the penalty band).
    */
  def coldSideDrainsImmunityHarder(helper: GameTestHelper): Unit = {
    val bandLow = CasualtiesBelowConfig.PenaltyBandLowCelsius.get()
    val bandHigh = CasualtiesBelowConfig.PenaltyBandHighCelsius.get()
    val deviation = 2.0

    // Emaciated players (food = 0): no fed regen competes with the temperature drain, but the
    // hungry drain (food below its threshold) and any dirt/poison drains still run identically in
    // both players — those common channels cancel in the difference below, isolating the
    // temperature contribution. The injected temperature decays every tick towards the world's
    // equilibrium, so re-pin it before each tick: this scenario measures the drain channels, not
    // the approach dynamics (those are covered by TemperatureScenarios).
    val coldPlayer = GameTestPlayers.createSurvivalPlayer(helper)
    val coldVitals = coldPlayer.vitals
    coldPlayer.getFoodData.setFoodLevel(0)
    coldPlayer.getFoodData.setSaturation(0.0f)
    val coldTarget = bandLow - deviation
    (1 to 1200).foreach { _ =>
      VitalsMutations.setBodyTemperature(coldVitals, coldTarget)
      InjuryProgression.tickForGameTest(coldPlayer)
    }
    val coldLoss = startImmune(coldVitals) - coldVitals.infection.immuneHealth

    val hotPlayer = GameTestPlayers.createSurvivalPlayer(helper)
    val hotVitals = hotPlayer.vitals
    hotPlayer.getFoodData.setFoodLevel(0)
    hotPlayer.getFoodData.setSaturation(0.0f)
    val hotTarget = bandHigh + deviation
    (1 to 1200).foreach { _ =>
      VitalsMutations.setBodyTemperature(hotVitals, hotTarget)
      InjuryProgression.tickForGameTest(hotPlayer)
    }
    val hotLoss = startImmune(hotVitals) - hotVitals.infection.immuneHealth

    // The cold side drains 1.0/min per °C, the hot side 0.5/min per °C, so with the deviation
    // pinned for one minute (1200 ticks) the cold player must lose deviation × (coldCoef −
    // hotCoef) × 1 minute = 2.0 × 0.5 = 1.0 more than the hot player. Common drains (hungry,
    // dirt, poison) are identical in both players and cancel in the difference. Consumers
    // (tickImmune, consciousness) run before the temperature approach step inside one tickPlayer
    // pass, so every tick reads the freshly pinned value — no relaxation drift to absorb.
    val coldCoef = CasualtiesBelowConfig.ColdImmuneDrainPerDegreePerMinute.get()
    val hotCoef = CasualtiesBelowConfig.HotImmuneDrainPerDegreePerMinute.get()
    val minutes = 1200.0 / 1200.0
    val expectedExcess = deviation * (coldCoef - hotCoef) * minutes
    helper.assertTrue(
      math.abs((coldLoss - hotLoss) - expectedExcess) < 0.1,
      s"equal deviations must drain cold ≈$expectedExcess more than hot, " +
        s"excess=${coldLoss - hotLoss} (cold=$coldLoss hot=$hotLoss)"
    )
    helper.assertTrue(
      coldLoss > hotLoss,
      s"cold side must drain harder than hot, cold=$coldLoss hot=$hotLoss"
    )
    helper.succeed()
  }

  // ---- Helpers -----------------------------------------------------------------

  /** Expected ceiling from the live config's default formula semantics:
    * `100 - coldDev * coldSlope - hotDev * hotSlope`.
    */
  private def expectedCeiling(coldDev: Double, hotDev: Double): Double = {
    val coldSlope = CasualtiesBelowConfig.ColdConsciousnessSlopePerDegree.get()
    val hotSlope = CasualtiesBelowConfig.HotConsciousnessSlopePerDegree.get()
    CasualtiesBelowConfig.TemperatureConsciousnessCeilingFormula
      .evaluate(coldDev, hotDev, coldSlope, hotSlope)
      .max(0.0)
      .min(100.0)
  }

  private def startImmune(
      vitals: dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
  ): Double =
    CasualtiesBelowConfig.MaxImmuneHealth.get().doubleValue()

  /** One manual tick settles consciousness onto the ceiling: recovery stops there (the bounded next
    * value clamps to the ceiling), and a healthy player (unconscious = false) with a positive
    * ceiling must not trip the knockout threshold.
    */
  private def assertConsciousnessAtCeiling(
      helper: GameTestHelper,
      player: ServerPlayer,
      ceiling: Double,
      message: String
  ): Unit = {
    val vitals = player.vitals
    helper.assertTrue(
      math.abs(vitals.consciousness.level - ceiling) < 0.001,
      s"$message: level ${vitals.consciousness.level} vs ceiling $ceiling"
    )
    helper.assertTrue(
      !vitals.consciousness.unconscious,
      s"$message: a ceiling must not knock the player out"
    )
  }
}
