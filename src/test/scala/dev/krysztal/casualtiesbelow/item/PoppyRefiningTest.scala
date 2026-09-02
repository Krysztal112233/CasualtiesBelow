package dev.krysztal.casualtiesbelow.item

import net.minecraft.SharedConstants
import net.minecraft.core.component.DataComponents
import net.minecraft.server.Bootstrap
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.PotionItem

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

final class PoppyRefiningTest {

  PoppyRefiningTest.initializeRegistries

  @Test
  def refinedExtractUsesTheSelectedThirtyPercentYield(): Unit = {
    assertEquals(30L, PoppyRefining.YieldNumerator)
    assertEquals(100L, PoppyRefining.YieldDenominator)
    assertEquals(8100L, PoppyRefining.refinedDroplets(FluidConstants.BOTTLE))
  }

  @Test
  def refinementFloorsOnlyPositiveOutputs(): Unit = {
    assertEquals(1L, PoppyRefining.refinedDroplets(4L))
    assertThrows(classOf[IllegalArgumentException], () => PoppyRefining.refinedDroplets(0L))
    assertThrows(classOf[IllegalArgumentException], () => PoppyRefining.refinedDroplets(-1L))
    assertThrows(classOf[IllegalArgumentException], () => PoppyRefining.refinedDroplets(3L))
    assertThrows(
      classOf[ArithmeticException],
      () => PoppyRefining.refinedDroplets(Long.MaxValue)
    )
  }

  @Test
  def refinedExtractContentsCarryTheirIdentityAndYieldedAmount(): Unit = {
    val contents = LiquidContents.RefinedPoppyExtract

    assertEquals("casualtiesbelow:refined_poppy_extract", contents.liquid.toString)
    assertEquals(8100L, contents.droplets)
  }

  @Test
  def brewingAdapterUsesAnOwnedCarrierPotionHiddenFromTooltips(): Unit = {
    assertTrue(classOf[PotionItem].isAssignableFrom(classOf[PoppyLiquidContainerItem]))
    assertEquals(
      "casualtiesbelow:poppy_carrier",
      PoppyPotions.CarrierKey.identifier().toString
    )
    assertTrue(
      PoppyLiquidContainerItem.TooltipDisplayWithoutAdapter
        .hiddenComponents()
        .contains(
          DataComponents.POTION_CONTENTS
        )
    )
  }

  @Test
  def automationWhitelistCoversOnlyTheThreeBottleSlots(): Unit = {
    assertFalse(PoppyRefining.isBottleSlot(-1))
    assertTrue(PoppyRefining.isBottleSlot(0))
    assertTrue(PoppyRefining.isBottleSlot(1))
    assertTrue(PoppyRefining.isBottleSlot(2))
    assertFalse(PoppyRefining.isBottleSlot(3))
    assertFalse(PoppyRefining.isBottleSlot(4))
  }

  // Vanilla items stand in for the mod's liquids: mod items cannot be constructed under the
  // frozen test registries, so the predicate core takes the container item as a parameter.
  @Test
  def bottleSlotAcceptsOnlyTheMatchingItemIntoAnEmptySlot(): Unit = {
    val crude = new ItemStack(Items.POPPY)
    val foreign = new ItemStack(Items.GLASS_BOTTLE)
    val occupied = new ItemStack(Items.GLASS_BOTTLE)

    assertTrue(PoppyRefining.canPlaceInBottleSlot(0, crude, ItemStack.EMPTY, Items.POPPY))
    assertTrue(PoppyRefining.canPlaceInBottleSlot(2, crude, ItemStack.EMPTY, Items.POPPY))
    assertFalse(PoppyRefining.canPlaceInBottleSlot(-1, crude, ItemStack.EMPTY, Items.POPPY))
    assertFalse(PoppyRefining.canPlaceInBottleSlot(3, crude, ItemStack.EMPTY, Items.POPPY))
    assertFalse(
      PoppyRefining.canPlaceInBottleSlot(0, ItemStack.EMPTY, ItemStack.EMPTY, Items.POPPY)
    )
    assertFalse(PoppyRefining.canPlaceInBottleSlot(0, foreign, ItemStack.EMPTY, Items.POPPY))
    assertFalse(PoppyRefining.canPlaceInBottleSlot(0, crude, occupied, Items.POPPY))
  }
}

private object PoppyRefiningTest {
  lazy val initializeRegistries: Unit = {
    SharedConstants.tryDetectVersion()
    Bootstrap.bootStrap()
  }
}
