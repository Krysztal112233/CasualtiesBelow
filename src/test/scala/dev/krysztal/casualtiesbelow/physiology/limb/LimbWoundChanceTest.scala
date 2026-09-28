package dev.krysztal.casualtiesbelow.physiology.limb

import dev.krysztal.casualtiesbelow.component.MutableLimbState
import dev.krysztal.casualtiesbelow.internal.Consts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

final class LimbWoundChanceTest {

  @Test
  def woundRiskSettingAffectsOnlyOnsetChance(): Unit = {
    val normalChance = Consts.Infection.InfectionChancePerTick
    assertEquals(normalChance, onsetChance(100.0, 0.0, 1.0), 1.0e-12)
    assertEquals(0.0, onsetChance(100.0, 100.0, 0.0), 1.0e-12)
    assertEquals(
      normalChance * 2.0 * 3.0,
      onsetChance(100.0, 100.0, 2.0),
      1.0e-12
    )
    assertEquals(
      normalChance * 0.5,
      onsetChance(50.0, 0.0, 1.0),
      1.0e-12
    )
  }

  private def onsetChance(skinDamage: Double, dirtiness: Double, riskMultiplier: Double): Double = {
    LimbCalc.woundOnsetChance(
      skinDamage,
      dirtiness,
      riskMultiplier,
      Consts.Infection.InfectionChancePerTick,
      MutableLimbState.MaxValue,
      Consts.Dirtiness.MaxValue,
      Consts.Dirtiness.InfectionChanceMultiplierAtMax
    )
  }
}
