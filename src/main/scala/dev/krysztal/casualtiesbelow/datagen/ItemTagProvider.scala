package dev.krysztal.casualtiesbelow.datagen

import java.util.concurrent.CompletableFuture

import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.tags.ItemTags
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowTags
import dev.krysztal.casualtiesbelow.item.FiberClothDryingInputs

/** Generates the mod's item tags: wound classification, shelf-dried plant fibers and food
  * discomfort tiers (see `Discomfort`). One provider for the whole item registry — Fabric datagen
  * rejects duplicate per-registry tag providers. Suspicious stew is absent on purpose: its effects
  * live in a stack component, so its tier is derived per stack in code.
  */
final class ItemTagProvider(
    output: FabricPackOutput,
    registries: CompletableFuture[HolderLookup.Provider]
) extends FabricTagsProvider[Item](output, Registries.ITEM, registries) {

  override protected def addTags(registries: HolderLookup.Provider): Unit = {
    // Sharp melee weapons cut skin open (see WoundProfiles). Nested tags, so any modded sword or
    // axe classifies automatically.
    builder(CasualtiesBelowTags.SharpMeleeItems)
      .addOptionalTag(ItemTags.SWORDS)
      .addOptionalTag(ItemTags.AXES)

    // Loose plant fibers that vanilla shelves can dry into fiber cloth.
    val fiberClothInputs = builder(CasualtiesBelowTags.DriesToFiberClothItems)
    FiberClothDryingInputs.BuiltInItems.foreach { item =>
      fiberClothInputs.add(itemKey(item))
    }

    // Discomfort tier 1: still edible, but raw/starchy/sickly-sweet — a brief queasiness.
    builder(CasualtiesBelowTags.Discomfort1Items)
      .add(
        itemKey(Items.POTATO),
        itemKey(Items.SALMON),
        itemKey(Items.COD),
        itemKey(Items.HONEY_BOTTLE)
      )

    // Discomfort tier 2: clearly hard to swallow — raw meat, dried kelp, teleport fruit.
    builder(CasualtiesBelowTags.Discomfort2Items)
      .add(
        itemKey(Items.DRIED_KELP),
        itemKey(Items.TROPICAL_FISH),
        itemKey(Items.RABBIT),
        itemKey(Items.BEEF),
        itemKey(Items.PORKCHOP),
        itemKey(Items.MUTTON),
        itemKey(Items.CHICKEN),
        itemKey(Items.CHORUS_FRUIT)
      )

    // Discomfort tier 3: rotten, poisonous or not human food at all.
    builder(CasualtiesBelowTags.Discomfort3Items)
      .add(
        itemKey(Items.POISONOUS_POTATO),
        itemKey(Items.PUFFERFISH),
        itemKey(Items.ROTTEN_FLESH),
        itemKey(Items.SPIDER_EYE)
      )

    // Nourishing soups, priced as one group by food_immune/tag/casualtiesbelow/healthy_soups.
    builder(CasualtiesBelowTags.HealthySoupsItems)
      .add(
        itemKey(Items.MUSHROOM_STEW),
        itemKey(Items.BEETROOT_SOUP),
        itemKey(Items.RABBIT_STEW)
      )
  }

  private def itemKey(item: Item) = BuiltInRegistries.ITEM.getResourceKey(item).orElseThrow()
}
