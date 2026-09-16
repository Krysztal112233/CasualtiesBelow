package dev.krysztal.casualtiesbelow.item

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

final class PoppyBottleFilteringTest {

  @Test
  def onlyFullUnfilteredContentsAreFilterable(): Unit = {
    assertTrue(
      PoppyProcessing.isUnfilteredBottleContents(LiquidContents.UnfilteredPoppyLiquid)
    )
  }

  @Test
  def partialOrForeignContentsAreRejected(): Unit = {
    assertFalse(
      PoppyProcessing.isUnfilteredBottleContents(
        LiquidContents(LiquidContents.UnfilteredPoppyLiquid.liquid, FluidConstants.BOTTLE - 1)
      )
    )
    assertFalse(
      PoppyProcessing.isUnfilteredBottleContents(LiquidContents.CrudePoppyLiquid)
    )
    assertFalse(
      PoppyProcessing.isUnfilteredBottleContents(LiquidContents.RefinedPoppyExtract)
    )
  }
}
