package dev.krysztal.casualtiesbelow.item

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi

/** Owns the mod's creative-mode tab and the display order of its content. */
object CasualtiesBelowItemGroup {
  private val Key: ResourceKey[CreativeModeTab] =
    ResourceKey.create(Registries.CREATIVE_MODE_TAB, CasualtiesBelowApi.id("casualtiesbelow"))

  def register(): Unit = {
    Registry.register(
      BuiltInRegistries.CREATIVE_MODE_TAB,
      Key,
      FabricCreativeModeTab
        .builder()
        .title(Component.translatable("itemGroup.casualtiesbelow"))
        .icon(() => ItemStack(CasualtiesBelowItems.BasicBandage))
        .displayItems { (_, output) =>
          output.accept(CasualtiesBelowItems.FiberCloth)
          output.accept(CasualtiesBelowItems.BasicBandage)
          output.accept(CasualtiesBelowItems.CrudeFilter)
          output.accept(CasualtiesBelowItems.CrudePoppyPaste)
          output.accept(CasualtiesBelowItems.CrudePoppyLiquid)
          output.accept(CasualtiesBelowItems.RefinedPoppyExtract)
          output.accept(CasualtiesBelowItems.Ampoule)
        }
        .build()
    )
  }
}
