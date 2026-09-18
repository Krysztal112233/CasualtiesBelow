package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import net.fabricmc.fabric.api.gametest.v1.GameTest

/** GameTest discovery entrypoint for the hygiene scenarios ([[HygieneScenarios]]). */
final class HygieneGameTests {

  @GameTest(maxTicks = 20)
  def dirtinessPassiveAccrualAccumulates(helper: GameTestHelper): Unit = {
    HygieneScenarios.passiveAccrualAccumulates(helper)
  }

  @GameTest(maxTicks = 20)
  def cauldronSoakWashesAndConsumesLevels(helper: GameTestHelper): Unit = {
    HygieneScenarios.cauldronSoakWashesAndConsumesLevels(helper)
  }

  @GameTest(maxTicks = 20)
  def waterWashRemovesDirtiness(helper: GameTestHelper): Unit = {
    HygieneScenarios.waterWashRemovesDirtiness(helper)
  }

  @GameTest(maxTicks = 20)
  def dirtyInjectionSeedsInfection(helper: GameTestHelper): Unit = {
    HygieneScenarios.dirtyInjectionSeedsInfection(helper)
  }
}
