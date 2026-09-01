package dev.krysztal.casualtiesbelow.item

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi

/** Registers the mod's items and their vanilla creative-tab placement. */
object CasualtiesBelowItems {
  private val FiberClothKey: ResourceKey[Item] =
    ResourceKey.create(Registries.ITEM, CasualtiesBelowApi.id("fiber_cloth"))
  private val BasicBandageKey: ResourceKey[Item] =
    ResourceKey.create(Registries.ITEM, CasualtiesBelowApi.id("basic_bandage"))

  val FiberCloth: Item = Registry.register(
    BuiltInRegistries.ITEM,
    FiberClothKey,
    Item(Item.Properties().setId(FiberClothKey).stacksTo(64))
  )

  val BasicBandage: Item = Registry.register(
    BuiltInRegistries.ITEM,
    BasicBandageKey,
    BasicBandageItem(
      Item.Properties().setId(BasicBandageKey).durability(BasicBandageItem.MaxUses)
    )
  )

  def register(): Unit = {
    CreativeModeTabEvents
      .modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
      .register(output => output.accept(FiberCloth))
    CreativeModeTabEvents
      .modifyOutputEvent(CreativeModeTabs.COMBAT)
      .register(output => output.accept(BasicBandage))
  }
}
