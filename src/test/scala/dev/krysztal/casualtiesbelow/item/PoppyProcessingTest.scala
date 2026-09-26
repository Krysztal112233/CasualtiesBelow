package dev.krysztal.casualtiesbelow.item

import java.lang.Integer as JInteger

import com.mojang.serialization.JsonOps
import net.minecraft.SharedConstants
import net.minecraft.core.Holder
import net.minecraft.core.NonNullList
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.Bootstrap
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LayeredCauldronBlock

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants

import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*

import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

final class PoppyProcessingTest {

  PoppyProcessingTest.initializeRegistries

  @Test
  def soakingRequiresAFullWaterCauldron(): Unit = {
    val oneLevel = waterCauldron(1)
    val twoLevels = waterCauldron(2)
    val full = waterCauldron(PoppyProcessing.FullCauldronLevel)

    assertFalse(PoppyProcessing.isFullWaterCauldron(Blocks.CAULDRON.defaultBlockState()))
    assertFalse(PoppyProcessing.isFullWaterCauldron(oneLevel))
    assertFalse(PoppyProcessing.isFullWaterCauldron(twoLevels))
    assertTrue(PoppyProcessing.isFullWaterCauldron(full))
  }

  @Test
  def soakingTakesTenMinutesAndAlwaysStartsAsAFullBatch(): Unit = {
    assertEquals(12000, PoppyProcessing.SoakDurationTicks)
    assertEquals(3, PoppyProcessing.FullCauldronLevel)
  }

  @Test
  def droppedIngredientConsumesExactlyOneWithoutMutatingTheSourceStack(): Unit = {
    val single = new ItemStack(Items.POPPY)
    val stack = new ItemStack(Items.POPPY, 4)

    assertTrue(PoppyProcessing.remainingAfterConsumingOne(single).isEmpty)
    assertEquals(3, PoppyProcessing.remainingAfterConsumingOne(stack).getCount)
    assertEquals(4, stack.getCount)
  }

  @Test
  def onlyAVanillaWaterPotionCountsAsAWaterBottle(): Unit = {
    val water = PotionContents.createItemStack(Items.POTION, Potions.WATER)
    val awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD)

    assertTrue(water.isWaterBottle)
    assertFalse(awkward.isWaterBottle)
    assertFalse(new ItemStack(Items.GLASS_BOTTLE).isWaterBottle)
  }

  @Test
  def inventorySearchReturnsTheFirstWaterBottle(): Unit = {
    val items = NonNullList.withSize[ItemStack](4, ItemStack.EMPTY)
    items.set(1, new ItemStack(Items.GLASS_BOTTLE))
    items.set(2, PotionContents.createItemStack(Items.POTION, Potions.WATER))
    items.set(3, PotionContents.createItemStack(Items.POTION, Potions.WATER))

    assertEquals(2, PoppyProcessing.findWaterBottleSlot(items))
    assertEquals(-1, PoppyProcessing.findWaterBottleSlot(NonNullList.create()))
  }

  @Test
  def filterAndLiquidContainerStackLimitsMatchTheInteractionContract(): Unit = {
    assertEquals(16, PoppyProcessing.CrudeFilterMaxStack)
    assertEquals(1, PoppyProcessing.LiquidContainerMaxStack)
  }

  @Test
  def crudePoppyLiquidContentsIdentifyOneFullBottle(): Unit = {
    val contents = LiquidContents.CrudePoppyLiquid

    assertEquals("casualtiesbelow:crude_poppy_liquid", contents.liquid.toString)
    assertEquals(FluidConstants.BOTTLE, contents.droplets)
  }

  @Test
  def liquidContentsCodecPreservesIdentityAndAcceptsOnlyPositiveAmounts(): Unit = {
    val encoded =
      LiquidContents.Codec.encodeStart(JsonOps.INSTANCE, LiquidContents.CrudePoppyLiquid)
    val decoded = LiquidContents.Codec.parse(JsonOps.INSTANCE, encoded.getOrThrow()).getOrThrow()

    assertEquals(LiquidContents.CrudePoppyLiquid, decoded)
    assertTrue(decodeContentsWithAmount(0).isError)
    assertTrue(decodeContentsWithAmount(-1).isError)
  }

  private def waterCauldron(level: Int) =
    Blocks.WATER_CAULDRON
      .defaultBlockState()
      .setValue[JInteger, JInteger](LayeredCauldronBlock.LEVEL, JInteger.valueOf(level))

  private def decodeContentsWithAmount(droplets: Long) = {
    val encoded = LiquidContents.Codec
      .encodeStart(JsonOps.INSTANCE, LiquidContents.CrudePoppyLiquid)
      .getOrThrow()
      .getAsJsonObject
    encoded.add("amount", new JsonPrimitive(droplets))
    LiquidContents.Codec.parse(JsonOps.INSTANCE, encoded)
  }
}

private object PoppyProcessingTest {
  lazy val initializeRegistries: Unit = {
    SharedConstants.tryDetectVersion()
    Bootstrap.bootStrap()
    bindCommonComponents(Items.POTION)
    bindCommonComponents(Items.GLASS_BOTTLE)
    bindCommonComponents(Items.POPPY)
  }

  private def bindCommonComponents(item: Item): Unit = {
    BuiltInRegistries.ITEM
      .wrapAsHolder(item)
      .asInstanceOf[Holder.Reference[Item]]
      .bindComponents(DataComponents.COMMON_ITEM_COMPONENTS)
  }
}
