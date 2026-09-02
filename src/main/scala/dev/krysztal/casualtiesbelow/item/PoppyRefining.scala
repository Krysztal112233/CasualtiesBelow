package dev.krysztal.casualtiesbelow.item

import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.Ingredient

import net.fabricmc.fabric.api.registry.FabricPotionBrewingBuilder

/** Registers the brewing-stand refinement step and its fixed special-recipe yield. */
private[casualtiesbelow] object PoppyRefining {
  private[item] val YieldNumerator = 30L
  private[item] val YieldDenominator = 100L

  private[item] def refinedDroplets(inputDroplets: Long): Long = {
    require(inputDroplets > 0, "refinement input must be positive")
    val output = Math.multiplyExact(inputDroplets, YieldNumerator) / YieldDenominator
    require(output > 0, "refinement output must be positive")
    output
  }

  private[casualtiesbelow] def isCrudePoppyLiquid(stack: ItemStack): Boolean =
    matches(stack, CasualtiesBelowItems.CrudePoppyLiquid)

  private[casualtiesbelow] def isBottleSlot(slot: Int): Boolean = slot >= 0 && slot < 3

  /** Pure core of the bottle-slot whitelist, split from the item binding so unit tests can exercise
    * it with vanilla items; mod items cannot be constructed under frozen test registries.
    */
  private[item] def canPlaceInBottleSlot(
      slot: Int,
      stack: ItemStack,
      current: ItemStack,
      crude: Item
  ): Boolean =
    isBottleSlot(slot) && current.isEmpty && matches(stack, crude)

  private[casualtiesbelow] def canPlaceInBottleSlot(
      slot: Int,
      stack: ItemStack,
      current: ItemStack
  ): Boolean =
    canPlaceInBottleSlot(slot, stack, current, CasualtiesBelowItems.CrudePoppyLiquid)

  private def matches(stack: ItemStack, item: Item): Boolean = !stack.isEmpty && stack.is(item)

  def register(): Unit = {
    FabricPotionBrewingBuilder.BUILD.register { builder =>
      builder.addContainer(CasualtiesBelowItems.CrudePoppyLiquid)
      builder
        .asInstanceOf[FabricPotionBrewingBuilder]
        .registerItemRecipe(
          CasualtiesBelowItems.CrudePoppyLiquid,
          Ingredient.of(Items.GLOWSTONE_DUST),
          CasualtiesBelowItems.RefinedPoppyExtract
        )
    }
  }
}
