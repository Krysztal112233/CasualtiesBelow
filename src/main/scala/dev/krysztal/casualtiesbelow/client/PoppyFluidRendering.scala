package dev.krysztal.casualtiesbelow.client

import net.minecraft.client.color.block.BlockTintSources
import net.minecraft.client.renderer.block.FluidModel
import net.minecraft.client.resources.model.sprite.Material
import net.minecraft.resources.Identifier
import net.minecraft.world.level.material.Fluid

import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry

import dev.krysztal.casualtiesbelow.fluid.PoppyFluids

/** Renders the poppy liquids with vanilla water geometry tinted to each liquid's color; dedicated
  * fluid textures can later replace the tints without touching any code.
  */
object PoppyFluidRendering {
  private val UnfilteredTint: Int = 0xff7a5c33
  private val CrudeTint: Int = 0xff6e1828
  private val RefinedTint: Int = 0xff9f244d

  def register(): Unit = {
    registerPair(
      PoppyFluids.UnfilteredPoppyLiquid,
      PoppyFluids.FlowingUnfilteredPoppyLiquid,
      UnfilteredTint
    )
    registerPair(
      PoppyFluids.CrudePoppyLiquid,
      PoppyFluids.FlowingCrudePoppyLiquid,
      CrudeTint
    )
    registerPair(
      PoppyFluids.RefinedPoppyExtract,
      PoppyFluids.FlowingRefinedPoppyExtract,
      RefinedTint
    )
  }

  private def registerPair(still: Fluid, flow: Fluid, tint: Int): Unit = {
    val model = new FluidModel.Unbaked(
      new Material(Identifier.withDefaultNamespace("block/water_still")),
      new Material(Identifier.withDefaultNamespace("block/water_flow")),
      new Material(Identifier.withDefaultNamespace("block/water_overlay")),
      BlockTintSources.constant(tint)
    )
    FluidRenderingRegistry.register(still, flow, model)
  }
}
