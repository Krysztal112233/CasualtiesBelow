package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import net.fabricmc.fabric.api.gametest.v1.GameTest

/** GameTest discovery entrypoint for the component persistence round-trip scenarios. */
final class PersistenceGameTests {

  @GameTest(maxTicks = 20)
  def vitalsRoundTripPreservesState(helper: GameTestHelper): Unit = {
    PersistenceScenarios.vitalsRoundTripPreservesState(helper)
  }

  @GameTest(maxTicks = 20)
  def bodyRoundTripPreservesLimbs(helper: GameTestHelper): Unit = {
    PersistenceScenarios.bodyRoundTripPreservesLimbs(helper)
  }
}
