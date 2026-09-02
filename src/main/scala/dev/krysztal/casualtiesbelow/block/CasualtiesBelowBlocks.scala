package dev.krysztal.casualtiesbelow.block

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.material.PushReaction

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi

/** Registers internal cauldron states used by poppy processing. They have no dedicated block items:
  * both map back to the vanilla cauldron item and drop it through generated loot tables.
  */
object CasualtiesBelowBlocks {
  private val SoakingPoppyCauldronKey: ResourceKey[Block] =
    ResourceKey.create(Registries.BLOCK, CasualtiesBelowApi.id("soaking_poppy_cauldron"))
  private val PoppyInfusionCauldronKey: ResourceKey[Block] =
    ResourceKey.create(Registries.BLOCK, CasualtiesBelowApi.id("poppy_infusion_cauldron"))

  val SoakingPoppyCauldron: SoakingPoppyCauldronBlock = Registry.register(
    BuiltInRegistries.BLOCK,
    SoakingPoppyCauldronKey,
    SoakingPoppyCauldronBlock(cauldronProperties(SoakingPoppyCauldronKey))
  )

  val PoppyInfusionCauldron: PoppyInfusionCauldronBlock = Registry.register(
    BuiltInRegistries.BLOCK,
    PoppyInfusionCauldronKey,
    PoppyInfusionCauldronBlock(cauldronProperties(PoppyInfusionCauldronKey))
  )

  def register(): Unit = {
    Item.BY_BLOCK.put(SoakingPoppyCauldron, Items.CAULDRON)
    Item.BY_BLOCK.put(PoppyInfusionCauldron, Items.CAULDRON)
  }

  private def cauldronProperties(key: ResourceKey[Block]): BlockBehaviour.Properties =
    BlockBehaviour.Properties
      .of()
      .mapColor(MapColor.STONE)
      .requiresCorrectToolForDrops()
      .strength(2.0f)
      .noOcclusion()
      // Scheduled ticks are position-bound, so moving an active batch could corrupt its timer.
      .pushReaction(PushReaction.BLOCK)
      .setId(key)
}
