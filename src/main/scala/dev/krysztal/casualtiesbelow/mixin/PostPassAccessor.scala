package dev.krysztal.casualtiesbelow.mixin

import java.util.Map

import com.mojang.blaze3d.buffers.GpuBuffer
import net.minecraft.client.renderer.PostPass

import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

/** Exposes the uniform-buffer map so a fixed JSON value can be replaced by a per-frame buffer. */
@Mixin(value = Array(classOf[PostPass]), remap = false)
trait PostPassAccessor {
  @Accessor(value = "customUniforms", remap = false)
  def casualtiesbelow$getCustomUniforms(): Map[String, GpuBuffer]
}
