package dev.krysztal.casualtiesbelow.item

import net.minecraft.SharedConstants
import net.minecraft.core.Holder
import net.minecraft.core.NonNullList
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.Bootstrap
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

final class AmpouleFillingTest {

  AmpouleFillingTest.initializeRegistries

  @Test
  def inventorySearchFindsOnlyEligibleRefinedExtract(): Unit = {
    val items = NonNullList.withSize[ItemStack](4, ItemStack.EMPTY)
    items.set(0, liquidStack(Items.GLASS_BOTTLE, LiquidContents.CrudePoppyLiquid))
    items.set(
      1,
      liquidStack(
        Items.POPPY,
        LiquidContents.RefinedPoppyAmpoule.copy(droplets = LiquidContents.AmpouleDroplets - 1L)
      )
    )
    items.set(2, liquidStack(Items.POPPY, LiquidContents.RefinedPoppyExtract))
    items.set(3, liquidStack(Items.POPPY, LiquidContents.RefinedPoppyExtract))

    assertEquals(2, AmpouleFilling.findRefinedExtractSlot(items, Items.POPPY))
    assertEquals(-1, AmpouleFilling.findRefinedExtractSlot(NonNullList.create(), Items.POPPY))
  }

  @Test
  def crudeContentsAreRejectedEvenOnTheExpectedContainerItem(): Unit = {
    val disguisedCrude = liquidStack(Items.POPPY, LiquidContents.CrudePoppyLiquid)

    assertFalse(AmpouleFilling.isEligibleRefinedExtract(disguisedCrude, Items.POPPY))
  }

  @Test
  def repeatedFillsDeductExactlyOneAmpouleDose(): Unit = {
    var source = liquidStack(Items.POPPY, LiquidContents.RefinedPoppyExtract)

    (9 to 1 by -1).foreach { dosesRemaining =>
      source = AmpouleFilling.sourceAfterFilling(source, Items.POPPY, Items.GLASS_BOTTLE)
      val contents = source.get(CasualtiesBelowDataComponents.LiquidContentsComponent)
      assertEquals(LiquidContents.AmpouleDroplets * dosesRemaining, contents.droplets)
    }

    source = AmpouleFilling.sourceAfterFilling(source, Items.POPPY, Items.GLASS_BOTTLE)
    assertTrue(source.is(Items.GLASS_BOTTLE))
    assertEquals(null, source.get(CasualtiesBelowDataComponents.LiquidContentsComponent))
  }

  @Test
  def exactDoseBecomesGlassBottleAndSubDoseRemainderIsRejected(): Unit = {
    val exact = liquidStack(Items.POPPY, LiquidContents.RefinedPoppyAmpoule)
    val insufficient = liquidStack(
      Items.POPPY,
      LiquidContents.RefinedPoppyAmpoule.copy(droplets = LiquidContents.AmpouleDroplets - 1L)
    )

    assertTrue(
      AmpouleFilling
        .sourceAfterFilling(exact, Items.POPPY, Items.GLASS_BOTTLE)
        .is(Items.GLASS_BOTTLE)
    )
    assertFalse(AmpouleFilling.isEligibleRefinedExtract(insufficient, Items.POPPY))
  }

  @Test
  def filledAmpouleCarriesOneDoseAndEmptyStacksConsumeOne(): Unit = {
    val filled = AmpouleFilling.filledAmpouleStack(Items.STICK)

    assertEquals(
      LiquidContents.RefinedPoppyAmpoule,
      filled.get(CasualtiesBelowDataComponents.LiquidContentsComponent)
    )
    assertFalse(AmpouleFilling.isEmptyAmpoule(filled, Items.STICK))
    val emptyAmpoules = new ItemStack(Items.STICK, 3)
    assertTrue(AmpouleFilling.isEmptyAmpoule(emptyAmpoules, Items.STICK))
    emptyAmpoules.consume(1, null)
    assertEquals(2, emptyAmpoules.getCount)
  }

  @Test
  def ampouleDoseIsEightHundredTenDroplets(): Unit = {
    assertEquals(810L, LiquidContents.AmpouleDroplets)
  }

  private def liquidStack(item: Item, contents: LiquidContents): ItemStack = {
    val stack = new ItemStack(item)
    stack.set(CasualtiesBelowDataComponents.LiquidContentsComponent, contents)
    stack
  }
}

private object AmpouleFillingTest {
  lazy val initializeRegistries: Unit = {
    SharedConstants.tryDetectVersion()
    Bootstrap.bootStrap()
    bindCommonComponents(Items.POPPY)
    bindCommonComponents(Items.GLASS_BOTTLE)
    bindCommonComponents(Items.STICK)
  }

  private def bindCommonComponents(item: Item): Unit = {
    BuiltInRegistries.ITEM
      .wrapAsHolder(item)
      .asInstanceOf[Holder.Reference[Item]]
      .bindComponents(DataComponents.COMMON_ITEM_COMPONENTS)
  }
}
