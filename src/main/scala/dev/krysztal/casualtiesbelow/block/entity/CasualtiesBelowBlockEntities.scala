package dev.krysztal.casualtiesbelow.block.entity

import java.util.Set

import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi
import dev.krysztal.casualtiesbelow.block.CasualtiesBelowBlocks

/** Registers the render-only block entity attached to an actively soaking poppy batch. */
object CasualtiesBelowBlockEntities {
  val SoakingPoppyCauldron: BlockEntityType[SoakingPoppyCauldronBlockEntity] =
    Registry.register(
      BuiltInRegistries.BLOCK_ENTITY_TYPE,
      CasualtiesBelowApi.id("soaking_poppy_cauldron"),
      new BlockEntityType[SoakingPoppyCauldronBlockEntity](
        (pos, state) => SoakingPoppyCauldronBlockEntity(pos, state),
        Set.of(CasualtiesBelowBlocks.SoakingPoppyCauldron)
      )
    )

  def register(): Unit = ()
}

/** Carries no process data; its only purpose is to anchor the client item renderer. */
final class SoakingPoppyCauldronBlockEntity(pos: BlockPos, state: BlockState)
    extends BlockEntity(CasualtiesBelowBlockEntities.SoakingPoppyCauldron, pos, state)
