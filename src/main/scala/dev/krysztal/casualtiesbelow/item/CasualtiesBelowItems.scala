package dev.krysztal.casualtiesbelow.item

import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.BucketItem
import net.minecraft.world.item.Item

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi
import dev.krysztal.casualtiesbelow.fluid.PoppyFluids

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
  private val UnmarkedSyringeKey: ResourceKey[Item] =
    ResourceKey.create(Registries.ITEM, CasualtiesBelowApi.id("unmarked_syringe"))
  private val CalibratedSyringeKey: ResourceKey[Item] =
    ResourceKey.create(Registries.ITEM, CasualtiesBelowApi.id("calibrated_syringe"))
  private val CrudePoppyPasteKey: ResourceKey[Item] =
    ResourceKey.create(Registries.ITEM, CasualtiesBelowApi.id("crude_poppy_paste"))
  private val CrudePoppyLiquidKey: ResourceKey[Item] =
    ResourceKey.create(Registries.ITEM, CasualtiesBelowApi.id("crude_poppy_liquid"))
  private val RefinedPoppyExtractKey: ResourceKey[Item] =
    ResourceKey.create(Registries.ITEM, CasualtiesBelowApi.id("refined_poppy_extract"))
  private val UnfilteredPoppyLiquidKey: ResourceKey[Item] =
    ResourceKey.create(Registries.ITEM, CasualtiesBelowApi.id("unfiltered_poppy_liquid"))
  private val UnfilteredPoppyLiquidBucketKey: ResourceKey[Item] =
    ResourceKey.create(Registries.ITEM, CasualtiesBelowApi.id("unfiltered_poppy_liquid_bucket"))
  private val CrudePoppyLiquidBucketKey: ResourceKey[Item] =
    ResourceKey.create(Registries.ITEM, CasualtiesBelowApi.id("crude_poppy_liquid_bucket"))
  private val RefinedPoppyExtractBucketKey: ResourceKey[Item] =
    ResourceKey.create(Registries.ITEM, CasualtiesBelowApi.id("refined_poppy_extract_bucket"))

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

  val UnmarkedSyringe: SyringeItem = Registry.register(
    BuiltInRegistries.ITEM,
    UnmarkedSyringeKey,
    SyringeItem(
      Item.Properties().setId(UnmarkedSyringeKey).stacksTo(16),
      calibrated = false
    )
  )

  val CalibratedSyringe: SyringeItem = Registry.register(
    BuiltInRegistries.ITEM,
    CalibratedSyringeKey,
    SyringeItem(
      Item.Properties().setId(CalibratedSyringeKey).stacksTo(16),
      calibrated = true
    )
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

  // The unfiltered bottle is a plain component-carrying item: it is never brewable, so unlike the
  // two potion-carrier bottles it needs no brewing adapter.
  val UnfilteredPoppyLiquid: Item = Registry.register(
    BuiltInRegistries.ITEM,
    UnfilteredPoppyLiquidKey,
    Item(
      Item
        .Properties()
        .setId(UnfilteredPoppyLiquidKey)
        .stacksTo(PoppyProcessing.LiquidContainerMaxStack)
        .component(
          CasualtiesBelowDataComponents.LiquidContentsComponent,
          LiquidContents.UnfilteredPoppyLiquid
        )
    )
  )

  val UnfilteredPoppyLiquidBucket: Item = Registry.register(
    BuiltInRegistries.ITEM,
    UnfilteredPoppyLiquidBucketKey,
    BucketItem(
      PoppyFluids.UnfilteredPoppyLiquid,
      Item.Properties().setId(UnfilteredPoppyLiquidBucketKey).stacksTo(1)
    )
  )

  val CrudePoppyLiquidBucket: Item = Registry.register(
    BuiltInRegistries.ITEM,
    CrudePoppyLiquidBucketKey,
    BucketItem(
      PoppyFluids.CrudePoppyLiquid,
      Item.Properties().setId(CrudePoppyLiquidBucketKey).stacksTo(1)
    )
  )

  val RefinedPoppyExtractBucket: Item = Registry.register(
    BuiltInRegistries.ITEM,
    RefinedPoppyExtractBucketKey,
    BucketItem(
      PoppyFluids.RefinedPoppyExtract,
      Item.Properties().setId(RefinedPoppyExtractBucketKey).stacksTo(1)
    )
  )

  def register(): Unit = CasualtiesBelowItemGroup.register()
}
