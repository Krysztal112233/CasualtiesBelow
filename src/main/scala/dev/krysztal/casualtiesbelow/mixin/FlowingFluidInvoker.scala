package dev.krysztal.casualtiesbelow.mixin

import scala.annotation.static

import net.minecraft.world.level.material.FlowingFluid
import net.minecraft.world.level.material.FluidState

import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Invoker

/** Exposes the protected static legacy-level helper so mod fluids can encode their state into
  * LiquidBlock.LEVEL without reimplementing the vanilla mapping.
  */
@Mixin(value = Array(classOf[FlowingFluid]), remap = false)
trait FlowingFluidInvoker

object FlowingFluidInvoker {

  @static
  @Invoker(value = "getLegacyLevel", remap = false)
  def callGetLegacyLevel(state: FluidState): Int =
    throw new AssertionError("Mixin transformation did not apply to FlowingFluidInvoker")
}
