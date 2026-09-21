package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import net.fabricmc.fabric.api.gametest.v1.GameTest

/** GameTest discovery entrypoint for the sweating scenarios ([[SweatScenarios]]). All scenarios
  * drive ticks synchronously, so the tick budget stays tiny.
  */
final class SweatGameTests {

  @GameTest(maxTicks = 20)
  def hotExertionProducesSweat(helper: GameTestHelper): Unit = {
    SweatScenarios.hotExertionProducesSweat(helper)
  }

  @GameTest(maxTicks = 20)
  def coolCoreSuppressesSweat(helper: GameTestHelper): Unit = {
    SweatScenarios.coolCoreSuppressesSweat(helper)
  }

  @GameTest(maxTicks = 20)
  def sweatingAccumulatesDirtinessFaster(helper: GameTestHelper): Unit = {
    SweatScenarios.sweatingAccumulatesDirtinessFaster(helper)
  }
}
