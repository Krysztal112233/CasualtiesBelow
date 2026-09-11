package dev.krysztal.casualtiesbelow.physiology.progression

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

final class InjuryProgressionTest {

  @Test
  def withdrawalHyperalgesiaScalesInfectionPainGrant(): Unit = {
    assertEquals(
      10.125,
      InjuryProgression.infectionPainAfterGrant(10.0, 0.1, 1.25),
      1.0e-9
    )
    assertEquals(
      10.1,
      InjuryProgression.infectionPainAfterGrant(10.0, 0.1, 1.0),
      1.0e-9
    )
  }
}
