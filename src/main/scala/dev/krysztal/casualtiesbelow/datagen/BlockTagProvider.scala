package dev.krysztal.casualtiesbelow.datagen

import java.util.concurrent.CompletableFuture

import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowTags
import dev.krysztal.casualtiesbelow.block.CasualtiesBelowBlocks

/** Generates the mod's block tags: poppy-processing cauldron classification, digging dirtiness
  * sources (see `hygiene.DirtinessSources`) and ambient heat/cold source tiers (see `temperature`).
  * One provider for the whole block registry — Fabric datagen rejects duplicate per-registry tag
  * providers.
  */
final class BlockTagProvider(
    output: FabricPackOutput,
    registries: CompletableFuture[HolderLookup.Provider]
) extends FabricTagsProvider[Block](output, Registries.BLOCK, registries) {

  override protected def addTags(registries: HolderLookup.Provider): Unit = {
    val poppyCauldrons = Seq(
      blockKey(CasualtiesBelowBlocks.SoakingPoppyCauldron),
      blockKey(CasualtiesBelowBlocks.PoppyInfusionCauldron)
    )
    builder(BlockTags.CAULDRONS).add(poppyCauldrons*)
    builder(BlockTags.MINEABLE_WITH_PICKAXE).add(poppyCauldrons*)
    builder(CasualtiesBelowTags.DiggableDirtyBlocks).add(
      Seq(
        Blocks.DIRT,
        Blocks.GRASS_BLOCK,
        Blocks.PODZOL,
        Blocks.MYCELIUM,
        Blocks.COARSE_DIRT,
        Blocks.ROOTED_DIRT,
        Blocks.DIRT_PATH,
        Blocks.FARMLAND,
        Blocks.SAND,
        Blocks.RED_SAND,
        Blocks.GRAVEL,
        Blocks.CLAY,
        Blocks.MUD,
        Blocks.MUDDY_MANGROVE_ROOTS,
        Blocks.SOUL_SAND,
        Blocks.SOUL_SOIL
      ).map(blockKey)*
    )
    builder(CasualtiesBelowTags.DiggableDustlessBlocks)
      .addOptionalTag(BlockTags.LEAVES)
      .addOptionalTag(BlockTags.WOOL)
      .addOptionalTag(BlockTags.LOGS)
      .addOptionalTag(BlockTags.PLANKS)
      .addOptionalTag(ConventionalBlockTags.GLASS_BLOCKS)
      .addOptionalTag(ConventionalBlockTags.GLASS_PANES)
      .add(
        Seq(
          Blocks.ICE,
          Blocks.PACKED_ICE,
          Blocks.BLUE_ICE,
          Blocks.FROSTED_ICE,
          Blocks.PUMPKIN,
          Blocks.CARVED_PUMPKIN,
          Blocks.JACK_O_LANTERN,
          Blocks.MELON,
          Blocks.HAY_BLOCK,
          Blocks.SPONGE,
          Blocks.WET_SPONGE,
          Blocks.SLIME_BLOCK,
          Blocks.HONEY_BLOCK
        ).map(blockKey)*
      )

    // Ambient heat tier 3 (extreme): vanilla burn mechanics — lava, still or cauldron-contained.
    builder(CasualtiesBelowTags.HeatSource3Blocks).add(
      blockKey(Blocks.LAVA),
      blockKey(Blocks.LAVA_CAULDRON)
    )

    // Ambient heat tier 2 (strong): open flames and hot floors. Nesting vanilla's tags classifies
    // modded campfires and fires automatically.
    builder(CasualtiesBelowTags.HeatSource2Blocks)
      .addOptionalTag(BlockTags.CAMPFIRES)
      .addOptionalTag(BlockTags.FIRE)
      .add(blockKey(Blocks.MAGMA_BLOCK))

    // Ambient heat tier 1 (weak): enclosed or small flames. Furnaces and candles only radiate while
    // lit; the tag is block-level, so consumers check `LIT` themselves. Vanilla's lanterns tag
    // covers soul and copper-weathering variants.
    builder(CasualtiesBelowTags.HeatSource1Blocks)
      .addOptionalTag(BlockTags.LANTERNS)
      .addOptionalTag(BlockTags.CANDLES)
      .addOptionalTag(BlockTags.CANDLE_CAKES)
      .add(
        Seq(
          Blocks.FURNACE,
          Blocks.SMOKER,
          Blocks.BLAST_FURNACE,
          Blocks.TORCH,
          Blocks.WALL_TORCH,
          Blocks.COPPER_TORCH,
          Blocks.COPPER_WALL_TORCH,
          Blocks.SOUL_TORCH,
          Blocks.SOUL_WALL_TORCH,
          Blocks.JACK_O_LANTERN
        ).map(blockKey)*
      )

    // Ambient cold tier 3 (freezing): vanilla freeze mechanics — powder snow, loose or contained.
    builder(CasualtiesBelowTags.ColdSource3Blocks).add(
      blockKey(Blocks.POWDER_SNOW),
      blockKey(Blocks.POWDER_SNOW_CAULDRON)
    )

    // Ambient cold tier 2 (strong): permanent ice and solid snow. Vanilla's #ice and #snow span
    // several tiers, so members are listed per block instead of nested.
    builder(CasualtiesBelowTags.ColdSource2Blocks).add(
      blockKey(Blocks.BLUE_ICE),
      blockKey(Blocks.PACKED_ICE),
      blockKey(Blocks.SNOW_BLOCK)
    )

    // Ambient cold tier 1 (weak): meltable or transient cold.
    builder(CasualtiesBelowTags.ColdSource1Blocks).add(
      blockKey(Blocks.ICE),
      blockKey(Blocks.SNOW),
      blockKey(Blocks.FROSTED_ICE)
    )
  }

  private def blockKey(block: Block) =
    BuiltInRegistries.BLOCK.getResourceKey(block).orElseThrow()
}
