package dev.krysztal.casualtiesbelow.item

import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.PotionItem
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.component.TooltipDisplay
import net.minecraft.world.item.context.UseOnContext

/** Compatibility carrier for logical poppy liquids processed by the vanilla brewing stand.
  *
  * Vanilla's brewing registry accepts only [[PotionItem]] containers with non-empty potion
  * contents. These items carry the internal no-effect [[PoppyPotions.PoppyCarrier]] instead of
  * water so no vanilla water-base mix accepts them, omit consumable components, suppress
  * PotionItem's water-to-mud behavior and potion-derived name, and hide the adapter potion contents
  * from the tooltip.
  */
final class PoppyLiquidContainerItem(properties: Item.Properties) extends PotionItem(properties) {

  /** PotionItem forces water onto default instances; re-apply the carrier so default, crafted, and
    * brewed stacks stay component-identical.
    */
  override def getDefaultInstance(): ItemStack = {
    val stack = super.getDefaultInstance()
    stack.set(DataComponents.POTION_CONTENTS, PoppyLiquidContainerItem.BrewingAdapterContents)
    stack
  }

  override def useOn(context: UseOnContext): InteractionResult = InteractionResult.PASS

  /** Honors stack-level item-name overrides while never deriving the name from potion contents. */
  override def getName(stack: ItemStack): Component =
    stack.getOrDefault(DataComponents.ITEM_NAME, Component.translatable(getDescriptionId()))
}

private[item] object PoppyLiquidContainerItem {

  /** Non-empty carrier contents required by the brewing registry. Lazily bound so frozen-registry
    * unit tests never trigger potion registration.
    */
  lazy val BrewingAdapterContents: PotionContents = new PotionContents(PoppyPotions.PoppyCarrier)

  val TooltipDisplayWithoutAdapter: TooltipDisplay =
    TooltipDisplay.DEFAULT.withHidden(DataComponents.POTION_CONTENTS, true)
}
