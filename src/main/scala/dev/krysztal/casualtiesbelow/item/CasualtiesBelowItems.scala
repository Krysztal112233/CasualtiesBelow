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
  private val CrudeFilterKey: ResourceKey[Item] =
    ResourceKey.create(Registries.ITEM, CasualtiesBelowApi.id("crude_filter"))
  private val CrudePoppyPasteKey: ResourceKey[Item] =
    ResourceKey.create(Registries.ITEM, CasualtiesBelowApi.id("crude_poppy_paste"))
  private val CrudePoppyLiquidKey: ResourceKey[Item] =
    ResourceKey.create(Registries.ITEM, CasualtiesBelowApi.id("crude_poppy_liquid"))

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

  val CrudeFilter: Item = Registry.register(
    BuiltInRegistries.ITEM,
    CrudeFilterKey,
    Item(Item.Properties().setId(CrudeFilterKey).stacksTo(PoppyProcessing.CrudeFilterMaxStack))
  )

  val CrudePoppyPaste: Item = Registry.register(
    BuiltInRegistries.ITEM,
    CrudePoppyPasteKey,
    Item(Item.Properties().setId(CrudePoppyPasteKey).stacksTo(64))
  )

  val CrudePoppyLiquid: Item = Registry.register(
    BuiltInRegistries.ITEM,
    CrudePoppyLiquidKey,
    Item(
      Item
        .Properties()
        .setId(CrudePoppyLiquidKey)
        .stacksTo(PoppyProcessing.LiquidContainerMaxStack)
        .component(
          CasualtiesBelowDataComponents.LiquidContentsComponent,
          LiquidContents.CrudePoppyLiquid
        )
    )
  )

  def register(): Unit = {
    CreativeModeTabEvents
      .modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
      .register { output =>
        output.accept(FiberCloth)
        output.accept(CrudePoppyPaste)
        output.accept(CrudeFilter)
        output.accept(CrudePoppyLiquid)
      }
    CreativeModeTabEvents
      .modifyOutputEvent(CreativeModeTabs.COMBAT)
      .register(output => output.accept(BasicBandage))
  }
}
