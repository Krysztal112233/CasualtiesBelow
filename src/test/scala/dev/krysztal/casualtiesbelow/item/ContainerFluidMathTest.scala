package dev.krysztal.casualtiesbelow.item

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

final class ContainerFluidMathTest {

  @Test
  def insertClampsToRemainingCapacity(): Unit = {
    assertEquals(0L, ContainerFluidMath.clampedInsert(27000L, 27000L, 1000L))
    assertEquals(1000L, ContainerFluidMath.clampedInsert(26000L, 27000L, 1000L))
    assertEquals(1000L, ContainerFluidMath.clampedInsert(26000L, 27000L, 5000L))
  }

  @Test
  def insertRejectsNonPositiveRequests(): Unit = {
    assertEquals(0L, ContainerFluidMath.clampedInsert(0L, 27000L, 0L))
    assertEquals(0L, ContainerFluidMath.clampedInsert(0L, 27000L, -5L))
  }

  @Test
  def extractClampsToHeldAmount(): Unit = {
    assertEquals(0L, ContainerFluidMath.clampedExtract(0L, 1000L))
    assertEquals(0L, ContainerFluidMath.clampedExtract(500L, 0L))
    assertEquals(500L, ContainerFluidMath.clampedExtract(500L, 5000L))
    assertEquals(500L, ContainerFluidMath.clampedExtract(500L, 500L))
  }

  @Test
  def syringeDrawRequiresOneFullDose(): Unit = {
    assertEquals(0L, ContainerFluidMath.syringeDrawAmount(809L, 810L))
    assertEquals(810L, ContainerFluidMath.syringeDrawAmount(810L, 810L))
    assertEquals(810L, ContainerFluidMath.syringeDrawAmount(81000L, 810L))
  }
}
