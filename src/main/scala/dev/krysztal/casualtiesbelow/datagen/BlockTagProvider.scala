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

/** Classifies transient poppy-processing blocks as ordinary mineable cauldrons. */
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
  }

  private def blockKey(block: Block) =
    BuiltInRegistries.BLOCK.getResourceKey(block).orElseThrow()
}
