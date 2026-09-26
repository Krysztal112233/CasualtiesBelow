package dev.krysztal.casualtiesbelow.datagen

import java.util.concurrent.CompletableFuture

import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.tags.BlockItemTags
import net.minecraft.tags.ItemTags
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowTags
import dev.krysztal.casualtiesbelow.item.FiberClothDryingInputs

/** Generates the mod's item tags: wound classification, shelf-dried plant fibers and food
  * discomfort tiers (see `Discomfort`), plus ambient heat/cold source tiers (see `temperature`).
  * One provider for the whole item registry — Fabric datagen rejects duplicate per-registry tag
  * providers. Suspicious stew is absent on purpose: its effects live in a stack component, so its
  * tier is derived per stack in code.
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
    builder(CasualtiesBelowTags.Discomfort1Food)
      .add(
        itemKey(Items.POTATO),
        itemKey(Items.SALMON),
        itemKey(Items.COD),
        itemKey(Items.HONEY_BOTTLE)
      )

    // Discomfort tier 2: clearly hard to swallow — raw meat, dried kelp, teleport fruit.
    builder(CasualtiesBelowTags.Discomfort2Food)
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
    builder(CasualtiesBelowTags.Discomfort3Food)
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

    // Ambient heat tier 3 (extreme): portable lava.
    builder(CasualtiesBelowTags.HeatSource3Items).add(itemKey(Items.LAVA_BUCKET))

    // Ambient heat tier 2 (strong): reliably-lit campfires, carried magma and blaze rods.
    builder(CasualtiesBelowTags.HeatSource2Items).add(
      itemKey(Items.CAMPFIRE),
      itemKey(Items.SOUL_CAMPFIRE),
      itemKey(Items.MAGMA_BLOCK),
      itemKey(Items.BLAZE_ROD)
    )

    // Ambient heat tier 1 (weak): small flames and heat thematics. LIT-dependent blocks (furnaces,
    // candles) are absent on purpose — their item forms are unlit. Vanilla's lanterns item tag
    // covers soul and copper-weathering variants.
    builder(CasualtiesBelowTags.HeatSource1Items)
      .addOptionalTag(BlockItemTags.LANTERNS.item())
      .add(
        itemKey(Items.TORCH),
        itemKey(Items.SOUL_TORCH),
        itemKey(Items.COPPER_TORCH),
        itemKey(Items.BLAZE_POWDER),
        itemKey(Items.MAGMA_CREAM),
        itemKey(Items.FIRE_CHARGE)
      )

    // Ambient cold tier 3 (freezing): vanilla freeze mechanics in portable form.
    builder(CasualtiesBelowTags.ColdSource3Items).add(
      itemKey(Items.POWDER_SNOW_BUCKET),
      itemKey(Items.BLUE_ICE)
    )

    // Ambient cold tier 2 (strong): permanent ice.
    builder(CasualtiesBelowTags.ColdSource2Items).add(itemKey(Items.PACKED_ICE))

    // Ambient cold tier 1 (weak): meltable cold and fire-extinguishing water.
    builder(CasualtiesBelowTags.ColdSource1Items).add(
      itemKey(Items.ICE),
      itemKey(Items.SNOW_BLOCK),
      itemKey(Items.SNOWBALL),
      itemKey(Items.WATER_BUCKET)
    )
  }

  private def itemKey(item: Item) = BuiltInRegistries.ITEM.getResourceKey(item).orElseThrow()
}
