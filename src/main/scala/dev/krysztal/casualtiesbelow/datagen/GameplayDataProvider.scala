package dev.krysztal.casualtiesbelow.datagen

import java.util.concurrent.CompletableFuture

import net.minecraft.core.HolderLookup
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider

import dev.krysztal.casualtiesbelow.api.data.CasualtiesBelowRegistries

/** Emits all built-in entries for the mod's synced gameplay-data registries. */
final class GameplayDataProvider(
    output: FabricPackOutput,
    registries: CompletableFuture[HolderLookup.Provider]
) extends FabricDynamicRegistryProvider(output, registries) {

  override protected def configure(
      registries: HolderLookup.Provider,
      entries: FabricDynamicRegistryProvider.Entries
  ): Unit = {
    addAll(registries, entries, CasualtiesBelowRegistries.WoundProfile)
    addAll(registries, entries, CasualtiesBelowRegistries.WoundRule)
    addAll(registries, entries, CasualtiesBelowRegistries.ArmorProtection)
    addAll(registries, entries, CasualtiesBelowRegistries.FallRules)
    addAll(registries, entries, CasualtiesBelowRegistries.HitLocation)
    // Discomfort intentionally has no built-in entries; tier tags remain its base data.
  }

  private def addAll[T](
      registries: HolderLookup.Provider,
      entries: FabricDynamicRegistryProvider.Entries,
      key: ResourceKey[? <: Registry[T]]
  ): Unit = {
    entries.addAll(registries.lookupOrThrow(key))
  }

  override def getName(): String = "Casualties: Below gameplay data"
}
