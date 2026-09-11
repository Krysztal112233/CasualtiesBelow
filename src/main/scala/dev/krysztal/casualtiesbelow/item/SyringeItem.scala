package dev.krysztal.casualtiesbelow.item

import java.lang.Double as JDouble
import java.util.function.Consumer

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.TooltipDisplay
import net.minecraft.world.level.Level

import dev.krysztal.casualtiesbelow.component.ComponentAccess
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

/** A single-use syringe. Empty syringes draw one inventory dose; filled syringes inject after a
  * hold-use action. Only calibrated syringes reveal their stored true dose.
  */
final class SyringeItem(properties: Item.Properties, val calibrated: Boolean)
    extends Item(properties) {

  override def getUseDuration(stack: ItemStack, user: LivingEntity): Int = {
    if (calibrated) CasualtiesBelowConfig.OpioidCalibratedSyringeUseDurationTicks.get()
    else CasualtiesBelowConfig.OpioidUnmarkedSyringeUseDurationTicks.get()
  }

  override def getUseAnimation(stack: ItemStack): ItemUseAnimation = ItemUseAnimation.BRUSH

  override def use(level: Level, player: Player, hand: InteractionHand): InteractionResult = {
    val stack = player.getItemInHand(hand)
    if (isEmpty(stack)) return SyringeFilling.interact(player, level, hand, this)

    player.startUsingItem(hand)
    InteractionResult.CONSUME
  }

  override def finishUsingItem(
      stack: ItemStack,
      level: Level,
      entity: LivingEntity
  ): ItemStack = {
    if (!level.isClientSide()) {
      entity match {
        case player: ServerPlayer =>
          val contents = stack.get(CasualtiesBelowDataComponents.SyringeContentsComponent)
          if (contents != null) {
            SyringeItem.inject(player, contents)
            player.awardStat(Stats.ITEM_USED.get(this))
            stack.consume(1, player)
          }
        case _ => ()
      }
    }
    stack
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

  def inject(player: ServerPlayer, contents: SyringeContents): Unit = {
    val vitals = ComponentAccess.vitals(player)
    VitalsMutations.setOpioidLevel(vitals, vitals.opioidLevel + contents.opioidDose)
    VitalsMutations.setDiscomfort(
      vitals,
      vitals.discomfort + discomfortPulse(contents.liquid)
    )
    VitalsMutations.syncNow(player)
  }

  private[item] def discomfortPulse(liquid: net.minecraft.resources.Identifier): Double = {
    if (liquid == LiquidContents.RefinedPoppyExtract.liquid) {
      CasualtiesBelowConfig.OpioidRefinedSyringeDiscomfort.get()
    } else if (liquid == LiquidContents.CrudePoppyLiquid.liquid) {
      CasualtiesBelowConfig.OpioidCrudeSyringeDiscomfort.get()
    } else 0.0
  }
}
