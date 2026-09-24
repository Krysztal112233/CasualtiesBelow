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

final class SyringeFillingTest {

  SyringeFillingTest.initializeRegistries

  @Test
  def filledAmpouleTakesPriorityOverCrudeBottle(): Unit = {
    val items = NonNullList.withSize[ItemStack](3, ItemStack.EMPTY)
    items.set(0, liquidStack(Items.GLASS_BOTTLE, LiquidContents.CrudePoppyLiquid))
    items.set(1, liquidStack(Items.POPPY, LiquidContents.RefinedPoppyAmpoule))

    val source = SyringeFilling.findSource(items, Items.POPPY, Items.GLASS_BOTTLE)

    assertEquals(Some(SyringeFilling.Source(1, SyringeFilling.SourceKind.RefinedAmpoule)), source)
  }

  @Test
  def refinedBottleIsRejectedWhileCrudeBottleIsAccepted(): Unit = {
    val refinedBottle = liquidStack(Items.GLASS_BOTTLE, LiquidContents.RefinedPoppyExtract)
    val crudeBottle = liquidStack(Items.GLASS_BOTTLE, LiquidContents.CrudePoppyLiquid)

    assertFalse(SyringeFilling.isEligibleCrudeBottle(refinedBottle, Items.GLASS_BOTTLE))
    assertTrue(SyringeFilling.isEligibleCrudeBottle(crudeBottle, Items.GLASS_BOTTLE))
  }

  @Test
  def crudeDrawDeductsOneDoseAndTurnsExactDoseIntoGlassBottle(): Unit = {
    val source = liquidStack(Items.POPPY, LiquidContents.CrudePoppyLiquid)
    val updated = SyringeFilling.crudeSourceAfterDraw(source, Items.GLASS_BOTTLE)
    val updatedContents = updated.get(CasualtiesBelowDataComponents.LiquidContentsComponent)

    assertEquals(
      LiquidContents.CrudePoppyLiquid.droplets - LiquidContents.AmpouleDroplets,
      updatedContents.droplets
    )

    val exact = liquidStack(
      Items.POPPY,
      LiquidContents.CrudePoppyLiquid.copy(droplets = LiquidContents.AmpouleDroplets)
    )
    assertTrue(
      SyringeFilling.crudeSourceAfterDraw(exact, Items.GLASS_BOTTLE).is(Items.GLASS_BOTTLE)
    )
  }

  @Test
  def crudeNormalSampleIsClampedToFixedRange(): Unit = {
    assertEquals(20.0, SyringeFilling.crudeBaseDose(-100.0, 40.0, 13.0), 1.0e-9)
    assertEquals(40.0, SyringeFilling.crudeBaseDose(0.0, 40.0, 13.0), 1.0e-9)
    assertEquals(60.0, SyringeFilling.crudeBaseDose(100.0, 40.0, 13.0), 1.0e-9)
  }

  @Test
  def unmarkedMeasurementJitterStaysWithinFifteenPercent(): Unit = {
    assertEquals(34.0, SyringeFilling.measuredDose(40.0, false, -1.0, 0.15), 1.0e-9)
    assertEquals(46.0, SyringeFilling.measuredDose(40.0, false, 1.0, 0.15), 1.0e-9)
    assertEquals(34.0, SyringeFilling.measuredDose(40.0, false, -100.0, 0.15), 1.0e-9)
    assertEquals(46.0, SyringeFilling.measuredDose(40.0, false, 100.0, 0.15), 1.0e-9)
  }

  @Test
  def calibratedMeasurementIsExactAndRefinedBaseIsFifty(): Unit = {
    val refinedBase = SyringeFilling.sampledBaseDose(
      SyringeFilling.SourceKind.RefinedAmpoule,
      gaussianSample = 100.0,
      refinedDose = 50.0,
      crudeMean = 40.0,
      crudeSigma = 13.0
    )

    assertEquals(50.0, refinedBase, 1.0e-9)
    assertEquals(50.0, SyringeFilling.measuredDose(refinedBase, true, -1.0, 0.15), 1.0e-9)
    assertEquals(50.0, SyringeFilling.measuredDose(refinedBase, true, 1.0, 0.15), 1.0e-9)
  }

  @Test
  def onlyFilledCalibratedSyringesShowTheirDose(): Unit = {
    assertTrue(SyringeItem.showsDose(calibrated = true, filled = true))
    assertFalse(SyringeItem.showsDose(calibrated = false, filled = true))
    assertFalse(SyringeItem.showsDose(calibrated = true, filled = false))
  }

  private def liquidStack(item: Item, contents: LiquidContents): ItemStack = {
    val stack = new ItemStack(item)
    stack.set(CasualtiesBelowDataComponents.LiquidContentsComponent, contents)
    stack
  }
}

private object SyringeFillingTest {
  lazy val initializeRegistries: Unit = {
    SharedConstants.tryDetectVersion()
    Bootstrap.bootStrap()
    bindCommonComponents(Items.POPPY)
    bindCommonComponents(Items.GLASS_BOTTLE)
  }

  private def bindCommonComponents(item: Item): Unit = {
    BuiltInRegistries.ITEM
      .wrapAsHolder(item)
      .asInstanceOf[Holder.Reference[Item]]
      .bindComponents(DataComponents.COMMON_ITEM_COMPONENTS)
  }
}
