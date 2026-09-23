package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import net.fabricmc.fabric.api.gametest.v1.GameTest

/** Runtime checks for status effects derived from opioid vitals. */
final class OpioidEffectGameTests {

  @GameTest(maxTicks = 20)
  def mirrorsVitalsToStatusEffects(helper: GameTestHelper): Unit = {
    OpioidEffectScenarios.mirrorsVitalsToStatusEffects(helper)
  }
}
