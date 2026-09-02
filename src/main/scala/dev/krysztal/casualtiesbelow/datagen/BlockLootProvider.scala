package dev.krysztal.casualtiesbelow.datagen

import java.util.concurrent.CompletableFuture

import net.minecraft.core.HolderLookup
import net.minecraft.world.level.block.Blocks

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider

import dev.krysztal.casualtiesbelow.block.CasualtiesBelowBlocks

/** Makes both transient poppy-processing states return the vanilla cauldron when broken. */
final class BlockLootProvider(
    output: FabricPackOutput,
    registries: CompletableFuture[HolderLookup.Provider]
) extends FabricBlockLootSubProvider(output, registries) {

  override def generate(): Unit = {
    dropOther(CasualtiesBelowBlocks.SoakingPoppyCauldron, Blocks.CAULDRON)
    dropOther(CasualtiesBelowBlocks.PoppyInfusionCauldron, Blocks.CAULDRON)
  }
}
