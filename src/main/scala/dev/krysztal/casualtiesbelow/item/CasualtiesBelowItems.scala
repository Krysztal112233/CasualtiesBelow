package dev.krysztal.casualtiesbelow.item

import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi

/** Registers the mod's items and dedicated creative-mode tab. */
object CasualtiesBelowItems {
  private val FiberClothKey: ResourceKey[Item] =
    ResourceKey.create(Registries.ITEM, CasualtiesBelowApi.id("fiber_cloth"))
  private val BasicBandageKey: ResourceKey[Item] =
    ResourceKey.create(Registries.ITEM, CasualtiesBelowApi.id("basic_bandage"))
  private val CrudeFilterKey: ResourceKey[Item] =
    ResourceKey.create(Registries.ITEM, CasualtiesBelowApi.id("crude_filter"))
  private val AmpouleKey: ResourceKey[Item] =
    ResourceKey.create(Registries.ITEM, CasualtiesBelowApi.id("ampoule"))
  private val CrudePoppyPasteKey: ResourceKey[Item] =
    ResourceKey.create(Registries.ITEM, CasualtiesBelowApi.id("crude_poppy_paste"))
  private val CrudePoppyLiquidKey: ResourceKey[Item] =
    ResourceKey.create(Registries.ITEM, CasualtiesBelowApi.id("crude_poppy_liquid"))
  private val RefinedPoppyExtractKey: ResourceKey[Item] =
    ResourceKey.create(Registries.ITEM, CasualtiesBelowApi.id("refined_poppy_extract"))

  private def poppyLiquidProperties(
      key: ResourceKey[Item],
      contents: LiquidContents
  ): Item.Properties =
    Item
      .Properties()
      .setId(key)
      .stacksTo(PoppyProcessing.LiquidContainerMaxStack)
      .component(CasualtiesBelowDataComponents.LiquidContentsComponent, contents)
      .component(DataComponents.POTION_CONTENTS, PoppyLiquidContainerItem.BrewingAdapterContents)
      .component(
        DataComponents.TOOLTIP_DISPLAY,
        PoppyLiquidContainerItem.TooltipDisplayWithoutAdapter
      )

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

  val Ampoule: Item = Registry.register(
    BuiltInRegistries.ITEM,
    AmpouleKey,
    Item(Item.Properties().setId(AmpouleKey).stacksTo(16))
  )

  val CrudePoppyPaste: Item = Registry.register(
    BuiltInRegistries.ITEM,
    CrudePoppyPasteKey,
    Item(Item.Properties().setId(CrudePoppyPasteKey).stacksTo(64))
  )

  val CrudePoppyLiquid: Item = Registry.register(
    BuiltInRegistries.ITEM,
    CrudePoppyLiquidKey,
    PoppyLiquidContainerItem(
      poppyLiquidProperties(CrudePoppyLiquidKey, LiquidContents.CrudePoppyLiquid)
    )
  )

  val RefinedPoppyExtract: Item = Registry.register(
    BuiltInRegistries.ITEM,
    RefinedPoppyExtractKey,
    PoppyLiquidContainerItem(
      poppyLiquidProperties(RefinedPoppyExtractKey, LiquidContents.RefinedPoppyExtract)
    )
  )

  def register(): Unit = CasualtiesBelowItemGroup.register()
}
