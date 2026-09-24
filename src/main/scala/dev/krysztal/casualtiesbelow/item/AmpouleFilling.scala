package dev.krysztal.casualtiesbelow.item

import java.util.List

import net.minecraft.sounds.SoundEvents
import net.minecraft.stats.Stats
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level

import net.fabricmc.fabric.api.event.player.UseItemCallback

import dev.krysztal.casualtiesbelow.internal.extension.Prelude.*

/** Server-authoritative filling of empty ampoules from refined poppy extract in the inventory. */
private[casualtiesbelow] object AmpouleFilling {

  def register(): Unit = {
    UseItemCallback.EVENT.register(new UseItemCallback {
      override def interact(
          player: Player,
          level: Level,
          hand: InteractionHand
      ): InteractionResult = AmpouleFilling.interact(player, level, hand)
    })
  }

  private def interact(
      player: Player,
      level: Level,
      hand: InteractionHand
  ): InteractionResult = {
    if (player.isSpectator()) return InteractionResult.PASS

    val heldStack = player.getItemInHand(hand)
    if (!isEmptyAmpoule(heldStack, CasualtiesBelowItems.Ampoule)) return InteractionResult.PASS
    if (level.isClientSide()) return InteractionResult.SUCCESS

    val inventory = player.getInventory
    val sourceSlot = findRefinedExtractSlot(
      inventory.getNonEquipmentItems,
      CasualtiesBelowItems.RefinedPoppyExtract
    )
    if (sourceSlot < 0) {
      player.sendOverlayMessage(
        "message.casualtiesbelow.ampoule.refined_extract_required".translatable()
      )
      return InteractionResult.SUCCESS
    }

    val source = inventory.getItem(sourceSlot)
    val filledAmpoule = filledAmpouleStack(CasualtiesBelowItems.Ampoule)
    heldStack.consume(1, player)

    if (player.hasInfiniteMaterials()) {
      if (!inventory.add(filledAmpoule)) player.drop(filledAmpoule, false)
    } else {
      inventory.setItem(
        sourceSlot,
        sourceAfterFilling(source, CasualtiesBelowItems.RefinedPoppyExtract, Items.GLASS_BOTTLE)
      )
      if (!inventory.add(filledAmpoule)) player.drop(filledAmpoule, false)
    }

    player.awardStat(Stats.ITEM_USED.get(CasualtiesBelowItems.Ampoule))
    level.playPlayerSound(player, SoundEvents.BOTTLE_FILL)
    InteractionResult.SUCCESS
  }

  private[item] def isEmptyAmpoule(stack: ItemStack, ampouleItem: Item): Boolean =
    !stack.isEmpty && stack.getItem == ampouleItem && stack.liquidContents.isEmpty

  private[item] def isEligibleRefinedExtract(stack: ItemStack, refinedItem: Item): Boolean = {
    if (stack.isEmpty || stack.getItem != refinedItem) return false

    stack.liquidContents.exists(contents =>
      contents.liquid == LiquidContents.RefinedPoppyExtract.liquid &&
        contents.droplets >= LiquidContents.AmpouleDroplets
    )
  }

  private[item] def findRefinedExtractSlot(items: List[ItemStack], refinedItem: Item): Int = {
    var slot = 0
    while (slot < items.size()) {
      if (isEligibleRefinedExtract(items.get(slot), refinedItem)) return slot
      slot += 1
    }
    -1
  }

  private[item] def filledAmpouleStack(ampouleItem: Item): ItemStack = {
    val stack = new ItemStack(ampouleItem)
    stack.withLiquidContents(LiquidContents.RefinedPoppyAmpoule)
    stack
  }

  private[item] def sourceAfterFilling(
      source: ItemStack,
      refinedItem: Item,
      emptyBottleItem: Item
  ): ItemStack = {
    require(isEligibleRefinedExtract(source, refinedItem), "source must contain one ampoule dose")

    val contents = source.liquidContents.getOrElse(
      throw IllegalArgumentException("source must contain one ampoule dose")
    )
    val remaining = contents.droplets - LiquidContents.AmpouleDroplets
    if (remaining == 0L) new ItemStack(emptyBottleItem)
    else {
      val updated = source.copy()
      updated.withLiquidContents(contents.copy(droplets = remaining))
      updated
    }
  }
}
