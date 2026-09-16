package dev.krysztal.casualtiesbelow.block

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.material.PushReaction

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi
import dev.krysztal.casualtiesbelow.fluid.PoppyFluids

/** Registers internal cauldron states used by poppy processing. They have no dedicated block items:
  * both map back to the vanilla cauldron item and drop it through generated loot tables.
  */
object CasualtiesBelowBlocks {
  private val SoakingPoppyCauldronKey: ResourceKey[Block] =
    ResourceKey.create(Registries.BLOCK, CasualtiesBelowApi.id("soaking_poppy_cauldron"))
  private val PoppyInfusionCauldronKey: ResourceKey[Block] =
    ResourceKey.create(Registries.BLOCK, CasualtiesBelowApi.id("poppy_infusion_cauldron"))

  // Fluid blocks share their id with the fluid itself (vanilla water does the same); they have no
  // block items and never drop anything, so they stay out of loot tables and tags entirely.
  private val UnfilteredPoppyLiquidBlockKey: ResourceKey[Block] =
    ResourceKey.create(Registries.BLOCK, CasualtiesBelowApi.id("unfiltered_poppy_liquid"))
  private val CrudePoppyLiquidBlockKey: ResourceKey[Block] =
    ResourceKey.create(Registries.BLOCK, CasualtiesBelowApi.id("crude_poppy_liquid"))
  private val RefinedPoppyExtractBlockKey: ResourceKey[Block] =
    ResourceKey.create(Registries.BLOCK, CasualtiesBelowApi.id("refined_poppy_extract"))

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

  val UnfilteredPoppyLiquid: LiquidBlock = Registry.register(
    BuiltInRegistries.BLOCK,
    UnfilteredPoppyLiquidBlockKey,
    LiquidBlock(
      PoppyFluids.UnfilteredPoppyLiquid,
      fluidBlockProperties(UnfilteredPoppyLiquidBlockKey)
    )
  )

  val CrudePoppyLiquid: LiquidBlock = Registry.register(
    BuiltInRegistries.BLOCK,
    CrudePoppyLiquidBlockKey,
    LiquidBlock(PoppyFluids.CrudePoppyLiquid, fluidBlockProperties(CrudePoppyLiquidBlockKey))
  )

  val RefinedPoppyExtract: LiquidBlock = Registry.register(
    BuiltInRegistries.BLOCK,
    RefinedPoppyExtractBlockKey,
    LiquidBlock(PoppyFluids.RefinedPoppyExtract, fluidBlockProperties(RefinedPoppyExtractBlockKey))
  )

  def register(): Unit = {
    Item.BY_BLOCK.put(SoakingPoppyCauldron, Items.CAULDRON)
    Item.BY_BLOCK.put(PoppyInfusionCauldron, Items.CAULDRON)
  }

  private def fluidBlockProperties(key: ResourceKey[Block]): BlockBehaviour.Properties =
    BlockBehaviour.Properties
      .of()
      .setId(key)
      .mapColor(MapColor.WATER)
      .replaceable()
      .noCollision()
      .strength(100.0f)
      .pushReaction(PushReaction.DESTROY)
      .noLootTable()
      .liquid()
      .sound(SoundType.EMPTY)

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
