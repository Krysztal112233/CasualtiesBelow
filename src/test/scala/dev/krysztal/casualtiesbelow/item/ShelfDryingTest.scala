package dev.krysztal.casualtiesbelow.item

import com.mojang.serialization.JsonOps
import net.minecraft.SharedConstants
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.Bootstrap
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

import dev.krysztal.casualtiesbelow.internal.extension.Prelude.*

import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

final class ShelfDryingTest {

  ShelfDryingTest.initializeItemComponents

  @Test
  def dryingRequiresThreeSuccessfulAdvances(): Unit = {
    assertEquals(1, ShelfDrying.advanceStage(0))
    assertEquals(2, ShelfDrying.advanceStage(1))
    assertEquals(ShelfDrying.RequiredAdvances, ShelfDrying.advanceStage(2))
  }

  @Test
  def successRollUsesStrictSeventyPercentBoundary(): Unit = {
    assertTrue(ShelfDrying.shouldAdvance(0.0f))
    assertTrue(ShelfDrying.shouldAdvance(Math.nextDown(ShelfDrying.SuccessChance)))
    assertFalse(ShelfDrying.shouldAdvance(ShelfDrying.SuccessChance))
    assertFalse(ShelfDrying.shouldAdvance(1.0f))
  }

  @Test
  def dryingStageCodecStoresOnlyInProgressStages(): Unit = {
    assertEquals(1, decodeStage(1).value)
    assertEquals(2, decodeStage(2).value)
    assertTrue(DryingStage.Codec.parse(JsonOps.INSTANCE, new JsonPrimitive(0)).error().isPresent)
    assertTrue(DryingStage.Codec.parse(JsonOps.INSTANCE, new JsonPrimitive(3)).error().isPresent)

    val fresh = new ItemStack(Items.VINE)
    assertNull(fresh.get(CasualtiesBelowDataComponents.DryingStageComponent))
    assertEquals(0, fresh.dryingStage)
  }

  @Test
  def inProgressStageRejectsNonPersistedValuesBeforeMutation(): Unit = {
    val input = new ItemStack(Items.VINE)

    assertThrows(
      classOf[IllegalArgumentException],
      () => input.withDryingStage(0)
    )
    assertThrows(
      classOf[IllegalArgumentException],
      () => input.withDryingStage(ShelfDrying.RequiredAdvances)
    )

    assertNull(input.get(CasualtiesBelowDataComponents.DryingStageComponent))
  }

  @Test
  def componentAppliesToWholeStackWithoutReducingItsLimit(): Unit = {
    val input = new ItemStack(Items.VINE, 64)
    input.withDryingStage(2)

    assertEquals(64, input.getCount)
    assertEquals(64, input.getMaxStackSize)

    val output = ShelfDrying.convertedStack(input, Items.PAPER)
    assertEquals(64, output.getCount)
    assertEquals(64, output.getMaxStackSize)
    assertNull(output.get(CasualtiesBelowDataComponents.DryingStageComponent))
  }

  @Test
  def onlyStacksAtTheSameDryingStageCanMerge(): Unit = {
    val fresh = new ItemStack(Items.VINE, 16)
    val stageOneA = withStage(1)
    val stageOneB = withStage(1)
    val stageTwo = withStage(2)

    assertFalse(ItemStack.isSameItemSameComponents(fresh, stageOneA))
    assertTrue(ItemStack.isSameItemSameComponents(stageOneA, stageOneB))
    assertFalse(ItemStack.isSameItemSameComponents(stageOneA, stageTwo))
  }

  private def decodeStage(value: Int): DryingStage =
    DryingStage.Codec.parse(JsonOps.INSTANCE, new JsonPrimitive(value)).getOrThrow()

  private def withStage(value: Int): ItemStack = {
    val stack = new ItemStack(Items.VINE, 16)
    stack.withDryingStage(value)
    stack
  }
}

private object ShelfDryingTest {
  lazy val initializeItemComponents: Unit = {
    SharedConstants.tryDetectVersion()
    Bootstrap.bootStrap()
    bindCommonComponents(Items.VINE)
    bindCommonComponents(Items.PAPER)
  }

  private def bindCommonComponents(item: Item): Unit = {
    BuiltInRegistries.ITEM
      .wrapAsHolder(item)
      .asInstanceOf[Holder.Reference[Item]]
      .bindComponents(DataComponents.COMMON_ITEM_COMPONENTS)
  }
}
