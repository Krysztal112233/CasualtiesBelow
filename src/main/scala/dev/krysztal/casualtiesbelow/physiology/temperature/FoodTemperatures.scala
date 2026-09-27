package dev.krysztal.casualtiesbelow.physiology.temperature

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents

import dev.krysztal.casualtiesbelow.api.item.datacomponent.FoodTemperature
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*

/** Applies the [[FoodTemperature]] component's one-shot core-temperature pulse when a food is eaten
  * (wired into the vanilla consumption flow by `ConsumableMixin`), and attaches the built-in
  * defaults to vanilla foods at bootstrap.
  */
private[casualtiesbelow] object FoodTemperatures {

  /** NOTE: HARD CODED FOR VANILLA MINECRAFT
    */
  private val Defaults: Map[Item, FoodTemperature] = Map(
    // Strong warm (+0.8): hot soups and stews.
    Items.MUSHROOM_STEW -> FoodTemperature(0.8),
    Items.RABBIT_STEW -> FoodTemperature(0.8),
    Items.BEETROOT_SOUP -> FoodTemperature(0.8),
    Items.SUSPICIOUS_STEW -> FoodTemperature(0.8),
    // Mid warm (+0.4): cooked meats and baked potato.
    Items.COOKED_BEEF -> FoodTemperature(0.4),
    Items.COOKED_PORKCHOP -> FoodTemperature(0.4),
    Items.COOKED_MUTTON -> FoodTemperature(0.4),
    Items.COOKED_CHICKEN -> FoodTemperature(0.4),
    Items.COOKED_COD -> FoodTemperature(0.4),
    Items.COOKED_SALMON -> FoodTemperature(0.4),
    Items.COOKED_RABBIT -> FoodTemperature(0.4),
    Items.BAKED_POTATO -> FoodTemperature(0.4),
    // Light warm (+0.2): baked goods and honey.
    Items.BREAD -> FoodTemperature(0.2),
    Items.COOKIE -> FoodTemperature(0.2),
    Items.PUMPKIN_PIE -> FoodTemperature(0.2),
    Items.HONEY_BOTTLE -> FoodTemperature(0.2),
    // Strong cool (-0.4): melon.
    Items.MELON_SLICE -> FoodTemperature(-0.4),
    // Light cool (-0.2): berries.
    Items.SWEET_BERRIES -> FoodTemperature(-0.2),
    Items.GLOW_BERRIES -> FoodTemperature(-0.2)
  )

  /** Attaches the defaults to vanilla foods via Fabric's default-component modification event. */
  def register(): Unit = {
    DefaultItemComponentEvents.MODIFY.register(context =>
      context.modify(
        (item: Item) => Defaults.contains(item),
        (builder, _, item) => builder.set(FoodTemperature.Component, Defaults(item))
      )
    )
  }

  def onFoodEaten(player: ServerPlayer, stack: ItemStack): Unit = {
    Option(stack.get(FoodTemperature.Component)).foreach { component =>
      player.vitals.setBodyTemperature(player.vitals.bodyTemperature + component.deltaCelsius)
    }
  }
}
