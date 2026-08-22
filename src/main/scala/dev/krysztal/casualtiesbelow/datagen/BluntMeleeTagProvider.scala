package dev.krysztal.casualtiesbelow.datagen

import java.util.concurrent.CompletableFuture

import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypeIds

import dev.krysztal.casualtiesbelow.damage.CasualtiesBelowTags

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider

/** Generates the mod's entity-type tags used for wound classification. */
final class BluntMeleeTagProvider(
    output: FabricPackOutput,
    registries: CompletableFuture[HolderLookup.Provider]
) extends FabricTagsProvider[EntityType[?]](output, Registries.ENTITY_TYPE, registries) {

  override protected def addTags(registries: HolderLookup.Provider): Unit = {
    // Unarmed attackers that slam rather than bite/scratch (see WoundProfiles): contact cubes,
    // heavy swingers, and constructs. Bare-handed attackers absent from this tag bite by default.
    builder(CasualtiesBelowTags.BluntMeleeEntities)
      .add(
        EntityTypeIds.SLIME,
        EntityTypeIds.MAGMA_CUBE,
        EntityTypeIds.ENDERMAN,
        EntityTypeIds.IRON_GOLEM,
        EntityTypeIds.WARDEN,
        EntityTypeIds.GOAT,
        EntityTypeIds.CREAKING
      )
  }
}
