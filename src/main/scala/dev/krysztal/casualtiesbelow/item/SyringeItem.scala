package dev.krysztal.casualtiesbelow.item

import java.lang.Double as JDouble
import java.util.function.Consumer

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.TooltipDisplay
import net.minecraft.world.level.Level

/** A single-use syringe. Empty syringes draw one inventory dose; filled syringes open the injection
  * screen (see [[dev.krysztal.casualtiesbelow.ui.InjectionScreen]]), a drag-to-inject minigame
  * whose batched progress is settled server-side. Only calibrated syringes reveal their stored true
  * dose.
  */
final class SyringeItem(properties: Item.Properties, val calibrated: Boolean)
    extends Item(properties) {

  override def use(level: Level, player: Player, hand: InteractionHand): InteractionResult = {
    val stack = player.getItemInHand(hand)
    if (isEmpty(stack)) return SyringeFilling.interact(player, level, hand, this)
    if (player.isSpectator()) return InteractionResult.PASS

    if (level.isClientSide()) {
      InjectionScreenHook.open(player, hand)
    }
    InteractionResult.SUCCESS
  }

  override def appendHoverText(
      stack: ItemStack,
      context: Item.TooltipContext,
      display: TooltipDisplay,
      consumer: Consumer[Component],
      flag: TooltipFlag
  ): Unit = {
    super.appendHoverText(stack, context, display, consumer, flag)
    val contents = stack.get(CasualtiesBelowDataComponents.SyringeContentsComponent)
    if (SyringeItem.showsDose(calibrated, contents != null)) {
      consumer.accept(
        Component
          .translatable(
            "tooltip.casualtiesbelow.syringe.opioid_dose",
            JDouble.valueOf(contents.opioidDose)
          )
          .withStyle(ChatFormatting.GRAY)
      )
    }
  }

  private[item] def isEmpty(stack: ItemStack): Boolean = {
    !stack.isEmpty &&
    stack.getItem == this &&
    stack.get(CasualtiesBelowDataComponents.SyringeContentsComponent) == null
  }
}

private[item] object SyringeItem {

  private[item] def showsDose(calibrated: Boolean, filled: Boolean): Boolean =
    calibrated && filled
}
