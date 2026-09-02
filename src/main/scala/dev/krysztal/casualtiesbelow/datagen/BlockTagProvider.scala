package dev.krysztal.casualtiesbelow.datagen

import java.util.concurrent.CompletableFuture

import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.Block

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider

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
  }

  private def blockKey(block: Block) =
    BuiltInRegistries.BLOCK.getResourceKey(block).orElseThrow()
}
