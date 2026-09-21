package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import net.fabricmc.fabric.api.gametest.v1.GameTest

/** GameTest discovery entrypoint for the temperature penalty-band scenarios
  * ([[PenaltyBandScenarios]]). All scenarios drive ticks synchronously, so the tick budget stays
  * tiny; the immunity comparison ticks one in-game minute (1200 ticks) synchronously.
  */
final class PenaltyBandGameTests {

  @GameTest(maxTicks = 20)
  def coldDeviationCapsConsciousness(helper: GameTestHelper): Unit = {
    PenaltyBandScenarios.coldDeviationCapsConsciousness(helper)
  }

  @GameTest(maxTicks = 20)
  def deeperColdDeviationLowersCeiling(helper: GameTestHelper): Unit = {
    PenaltyBandScenarios.deeperColdDeviationLowersCeiling(helper)
  }

  @GameTest(maxTicks = 20)
  def insideBandIsPressureFree(helper: GameTestHelper): Unit = {
    PenaltyBandScenarios.insideBandIsPressureFree(helper)
  }

  @GameTest(maxTicks = 20)
  def coldSideDrainsImmunityHarder(helper: GameTestHelper): Unit = {
    PenaltyBandScenarios.coldSideDrainsImmunityHarder(helper)
  }
}
