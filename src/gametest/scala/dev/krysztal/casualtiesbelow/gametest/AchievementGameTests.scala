package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import net.fabricmc.fabric.api.gametest.v1.GameTest

/** GameTest discovery entrypoint for the achievement scenarios ([[AchievementScenarios]]). */
final class AchievementGameTests {

  @GameTest(maxTicks = 20)
  def bandageObtainedGrantsAdvancement(helper: GameTestHelper): Unit = {
    AchievementScenarios.bandageObtainedGrantsAdvancement(helper)
  }

  @GameTest(maxTicks = 20)
  def refinedExtractObtainedGrantsAdvancement(helper: GameTestHelper): Unit = {
    AchievementScenarios.refinedExtractObtainedGrantsAdvancement(helper)
  }

  @GameTest(maxTicks = 20)
  def opioidOverdoseDeathGrantsAdvancement(helper: GameTestHelper): Unit = {
    AchievementScenarios.opioidOverdoseDeathGrantsAdvancement(helper)
  }

  @GameTest(maxTicks = 20)
  def hypoxiaDeathWithoutOpioidDoesNotGrant(helper: GameTestHelper): Unit = {
    AchievementScenarios.hypoxiaDeathWithoutOpioidDoesNotGrant(helper)
  }

  @GameTest(maxTicks = 20)
  def consciousnessMinimumGrantsAdvancement(helper: GameTestHelper): Unit = {
    AchievementScenarios.consciousnessMinimumGrantsAdvancement(helper)
  }

  @GameTest(maxTicks = 20)
  def consciousPlayerDoesNotGrantMinimum(helper: GameTestHelper): Unit = {
    AchievementScenarios.consciousPlayerDoesNotGrantMinimum(helper)
  }

  @GameTest(maxTicks = 20)
  def maxDiscomfortFoodGrantsAdvancement(helper: GameTestHelper): Unit = {
    AchievementScenarios.maxDiscomfortFoodGrantsAdvancement(helper)
  }

  @GameTest(maxTicks = 20)
  def ordinaryFoodDoesNotGrantMaxDiscomfort(helper: GameTestHelper): Unit = {
    AchievementScenarios.ordinaryFoodDoesNotGrantMaxDiscomfort(helper)
  }

  @GameTest(maxTicks = 20)
  def firstBleedingGrantsRoot(helper: GameTestHelper): Unit = {
    AchievementScenarios.firstBleedingGrantsRoot(helper)
  }

  @GameTest(maxTicks = 20)
  def hemostasisAfterNearMaxBleedingGrantsAdvancement(helper: GameTestHelper): Unit = {
    AchievementScenarios.hemostasisAfterNearMaxBleedingGrantsAdvancement(helper)
  }

  @GameTest(maxTicks = 20)
  def bleedingBelowThresholdDoesNotGrantHemostasis(helper: GameTestHelper): Unit = {
    AchievementScenarios.bleedingBelowThresholdDoesNotGrantHemostasis(helper)
  }
}
