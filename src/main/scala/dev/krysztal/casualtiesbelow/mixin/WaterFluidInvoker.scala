package dev.krysztal.casualtiesbelow.mixin

import net.minecraft.core.BlockPos
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.WaterFluid

import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Invoker

/** Exposes WaterFluid's block-drop semantics so the poppy fluids can delegate to the vanilla water
  * implementation instead of carrying a copy of its body.
  */
@Mixin(value = Array(classOf[WaterFluid]), remap = false)
trait WaterFluidInvoker {
  @Invoker(value = "beforeDestroyingBlock", remap = false)
  def casualtiesbelow$invokeBeforeDestroyingBlock(
      level: LevelAccessor,
      pos: BlockPos,
      state: BlockState
  ): Unit
}
