package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import net.fabricmc.fabric.api.gametest.v1.GameTest

/** GameTest discovery entrypoint for the body-temperature scenarios ([[TemperatureScenarios]]). All
  * scenarios drive ticks synchronously, so the tick budget stays tiny.
  */
final class TemperatureGameTests {

  @GameTest(maxTicks = 20)
  def comfortBoundsRespectConfigAndNormalizeOrder(helper: GameTestHelper): Unit = {
    TemperatureScenarios.comfortBoundsRespectConfigAndNormalizeOrder(helper)
  }

  @GameTest(maxTicks = 20)
  def coreApproachesEquilibriumFromBothSides(helper: GameTestHelper): Unit = {
    TemperatureScenarios.coreApproachesEquilibriumFromBothSides(helper)
  }

  @GameTest(maxTicks = 20)
  def immersionAcceleratesApproachAndSoaks(helper: GameTestHelper): Unit = {
    TemperatureScenarios.immersionAcceleratesApproachAndSoaks(helper)
  }

  @GameTest(maxTicks = 20)
  def exerciseHeatRaisesCore(helper: GameTestHelper): Unit = {
    TemperatureScenarios.exerciseHeatRaisesCore(helper)
  }

  @GameTest(maxTicks = 20)
  def onFireHeatsAndFlashDries(helper: GameTestHelper): Unit = {
    TemperatureScenarios.onFireHeatsAndFlashDries(helper)
  }

  @GameTest(maxTicks = 20)
  def hotFloorDamageHeats(helper: GameTestHelper): Unit = {
    TemperatureScenarios.hotFloorDamageHeats(helper)
  }

  @GameTest(maxTicks = 20)
  def campfireDamageHeats(helper: GameTestHelper): Unit = {
    TemperatureScenarios.campfireDamageHeats(helper)
  }

  @GameTest(maxTicks = 20)
  def lavaDamageOutranksBurning(helper: GameTestHelper): Unit = {
    TemperatureScenarios.lavaDamageOutranksBurning(helper)
  }

  @GameTest(maxTicks = 20)
  def unrelatedDamageDoesNotHeat(helper: GameTestHelper): Unit = {
    TemperatureScenarios.unrelatedDamageDoesNotHeat(helper)
  }

  @GameTest(maxTicks = 20)
  def dissipativePaysArmorBlockAndDirectBypasses(helper: GameTestHelper): Unit = {
    TemperatureScenarios.dissipativePaysArmorBlockAndDirectBypasses(helper)
  }

  @GameTest(maxTicks = 20)
  def biomeDownfallIsReadable(helper: GameTestHelper): Unit = {
    TemperatureScenarios.biomeDownfallIsReadable(helper)
  }
}
