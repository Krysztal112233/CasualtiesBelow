package dev.krysztal.casualtiesbelow.mixin

import scala.annotation.static

import net.minecraft.world.item.ItemStack

import dev.krysztal.casualtiesbelow.item.PoppyRefining

import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** Adds only crude poppy liquid to the vanilla brewing menu's bottle-slot whitelist. */
@Mixin(
  targets = Array("net.minecraft.world.inventory.BrewingStandMenu$PotionSlot"),
  remap = false
)
abstract class BrewingStandPotionSlotMixin

object BrewingStandPotionSlotMixin {

  @static
  @Inject(
    method = Array("mayPlaceItem"),
    at = Array(new At(value = "HEAD")),
    cancellable = true,
    remap = false
  )
  private def casualtiesbelow$allowCrudePoppyLiquid(
      stack: ItemStack,
      cir: CallbackInfoReturnable[Boolean]
  ): Unit = {
    if (PoppyRefining.isCrudePoppyLiquid(stack)) cir.setReturnValue(true)
  }
}
