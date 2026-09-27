package dev.krysztal.casualtiesbelow.physiology.temperature

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

import dev.krysztal.casualtiesbelow.api.item.datacomponent.FoodTemperature
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*

/** Applies the [[FoodTemperature]] component's one-shot core-temperature pulse when a food is
  * eaten. Wired into the vanilla consumption flow by `ConsumableMixin`.
  */
private[casualtiesbelow] object FoodTemperatures {

  def onFoodEaten(player: ServerPlayer, stack: ItemStack): Unit = {
    Option(stack.get(FoodTemperature.Component)).foreach { component =>
      player.vitals.setBodyTemperature(player.vitals.bodyTemperature + component.deltaCelsius)
    }
  }
}
