package dev.krysztal.casualtiesbelow.mixin

import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity

import dev.krysztal.casualtiesbelow.item.PoppyRefining

import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** Adds only crude poppy liquid to the brewing stand's automation bottle slots. */
@Mixin(value = Array(classOf[BrewingStandBlockEntity]), remap = false)
abstract class BrewingStandBlockEntityMixin {

  @Inject(
    method = Array("canPlaceItem"),
    at = Array(new At(value = "HEAD")),
    cancellable = true,
    remap = false
  )
  private def casualtiesbelow$allowCrudePoppyLiquid(
      slot: Int,
      stack: ItemStack,
      cir: CallbackInfoReturnable[Boolean]
  ): Unit = {
    if (!PoppyRefining.isBottleSlot(slot)) return
    // Match vanilla: automation may only fill an empty bottle slot.
    val current = this.asInstanceOf[BrewingStandBlockEntity].getItem(slot)
    if (PoppyRefining.canPlaceInBottleSlot(slot, stack, current)) cir.setReturnValue(true)
  }
}
