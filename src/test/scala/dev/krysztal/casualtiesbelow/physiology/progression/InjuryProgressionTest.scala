package dev.krysztal.casualtiesbelow.physiology.progression

import dev.krysztal.casualtiesbelow.physiology.limb.Limb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

final class InjuryProgressionTest {

  @Test
  def withdrawalHyperalgesiaScalesInfectionPainGrant(): Unit = {
    assertEquals(
      10.125,
      Limb.infectionPainAfterGrant(10.0, 0.1, 1.25),
      1.0e-9
    )
    assertEquals(
      10.1,
      Limb.infectionPainAfterGrant(10.0, 0.1, 1.0),
      1.0e-9
    )
  }
}
