package dev.krysztal.casualtiesbelow.item

import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries

import net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi

/** Registers item-stack components used by Casualties: Below. */
object CasualtiesBelowDataComponents {
  val DryingStageComponent: DataComponentType[DryingStage] = DataComponentType
    .builder[DryingStage]()
    .persistent(DryingStage.Codec)
    .build()
  val LiquidContentsComponent: DataComponentType[LiquidContents] = DataComponentType
    .builder[LiquidContents]()
    .persistent(LiquidContents.Codec)
    .build()
  val SyringeContentsComponent: DataComponentType[SyringeContents] = DataComponentType
    .builder[SyringeContents]()
    .persistent(SyringeContents.Codec)
    .build()

  def register(): Unit = {
    Registry.register(
      BuiltInRegistries.DATA_COMPONENT_TYPE,
      CasualtiesBelowApi.id("drying_stage"),
      DryingStageComponent
    )
    Registry.register(
      BuiltInRegistries.DATA_COMPONENT_TYPE,
      CasualtiesBelowApi.id("liquid_contents"),
      LiquidContentsComponent
    )
    Registry.register(
      BuiltInRegistries.DATA_COMPONENT_TYPE,
      CasualtiesBelowApi.id("syringe_contents"),
      SyringeContentsComponent
    )
    ItemComponentTooltipProviderRegistry.addLast(DryingStageComponent)
    ItemComponentTooltipProviderRegistry.addLast(LiquidContentsComponent)
  }
}
