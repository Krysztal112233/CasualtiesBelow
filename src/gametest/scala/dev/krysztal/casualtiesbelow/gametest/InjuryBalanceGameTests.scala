package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import net.fabricmc.fabric.api.gametest.v1.GameTest

/** GameTest discovery entrypoint for adjustable injury balance. */
final class InjuryBalanceGameTests {

  @GameTest(maxTicks = 20)
  def fallDamageSettingScalesPlayers(helper: GameTestHelper): Unit = {
    InjuryBalanceScenarios.fallDamageSettingScalesPlayers(helper)
  }

  @GameTest(maxTicks = 20)
  def clottingAndNaturalHealingSettingsAreIndependent(helper: GameTestHelper): Unit = {
    InjuryBalanceScenarios.clottingAndNaturalHealingSettingsAreIndependent(helper)
  }

  @GameTest(maxTicks = 20)
  def legPenaltySettingScalesSpeedAndJump(helper: GameTestHelper): Unit = {
    InjuryBalanceScenarios.legPenaltySettingScalesSpeedAndJump(helper)
  }
}
