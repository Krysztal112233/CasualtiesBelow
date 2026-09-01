package dev.krysztal.casualtiesbelow.mixin

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.ShelfBlock
import net.minecraft.world.level.block.state.BlockState

import dev.krysztal.casualtiesbelow.item.ShelfDrying

import org.spongepowered.asm.mixin.Mixin

/** Enables vanilla random ticking for every shelf state and delegates the selected tick to the
  * stack-component drying service. The constant result is required because block states cache this
  * flag during registry bootstrap.
  */
@Mixin(value = Array(classOf[ShelfBlock]), remap = false)
abstract class ShelfBlockMixin {

  protected def isRandomlyTicking(state: BlockState): Boolean = true

  protected def randomTick(
      state: BlockState,
      level: ServerLevel,
      pos: BlockPos,
      random: RandomSource
  ): Unit = {
    ShelfDrying.randomTick(state, level, pos, random)
  }
}
