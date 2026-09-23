package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import net.fabricmc.fabric.api.gametest.v1.GameTest

/** Runtime checks for status effects derived from physiology vitals. */
final class VitalsEffectGameTests {

  @GameTest(maxTicks = 20)
  def mirrorsVitalsToStatusEffects(helper: GameTestHelper): Unit = {
    VitalsEffectScenarios.mirrorsVitalsToStatusEffects(helper)
  }
}
