package dev.krysztal.casualtiesbelow.mixin

import net.minecraft.core.BlockPos
import net.minecraft.world.level.biome.Biome

import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Invoker

/** Exposes the coordinate-adjusted biome temperature used by vanilla precipitation checks. */
@Mixin(value = Array(classOf[Biome]), remap = false)
trait BiomeInvoker {
  @Invoker(value = "getTemperature", remap = false)
  def casualtiesbelow$invokeGetTemperature(pos: BlockPos, seaLevel: Int): Float
}
