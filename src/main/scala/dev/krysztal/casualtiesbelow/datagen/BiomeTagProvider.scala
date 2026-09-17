package dev.krysztal.casualtiesbelow.datagen

import java.util.concurrent.CompletableFuture

import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Biomes

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowTags

/** Marks murky-water biomes whose washing is less effective (swamps by default). */
final class BiomeTagProvider(
    output: FabricPackOutput,
    registries: CompletableFuture[HolderLookup.Provider]
) extends FabricTagsProvider[Biome](output, Registries.BIOME, registries) {

  override protected def addTags(registries: HolderLookup.Provider): Unit = {
    builder(CasualtiesBelowTags.DirtyWaterBiomes).add(Seq(Biomes.SWAMP, Biomes.MANGROVE_SWAMP)*)
  }
}
