package dev.krysztal.casualtiesbelow.datagen

import java.util.concurrent.CompletableFuture

import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.tags.ItemTags
import net.minecraft.world.item.Item

import dev.krysztal.casualtiesbelow.damage.CasualtiesBelowTags

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider

/** Generates the mod's item tags used for wound classification. */
final class SharpMeleeTagProvider(
    output: FabricPackOutput,
    registries: CompletableFuture[HolderLookup.Provider]
) extends FabricTagsProvider[Item](output, Registries.ITEM, registries) {

  override protected def addTags(registries: HolderLookup.Provider): Unit = {
    // Sharp melee weapons cut skin open (see WoundProfiles). Nested tags, so any modded sword or
    // axe classifies automatically.
    builder(CasualtiesBelowTags.SharpMeleeItems)
      .addOptionalTag(ItemTags.SWORDS)
      .addOptionalTag(ItemTags.AXES)
  }
}
