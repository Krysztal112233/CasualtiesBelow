package dev.krysztal.casualtiesbelow.mixin

import java.util.List

import net.minecraft.client.renderer.PostChain
import net.minecraft.client.renderer.PostPass

import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

/** Gives the client post-effect controller access to the passes rebuilt by each shader reload. */
@Mixin(value = Array(classOf[PostChain]), remap = false)
trait PostChainAccessor {
  @Accessor(value = "passes", remap = false)
  def casualtiesbelow$getPasses(): List[PostPass]
}
