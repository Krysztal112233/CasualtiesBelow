package dev.krysztal.casualtiesbelow.ui

import scala.jdk.CollectionConverters.*

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.renderer.PostChain

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.mixin.PostChainAccessor
import dev.krysztal.casualtiesbelow.mixin.PostPassAccessor

import org.lwjgl.system.MemoryStack

/** One named std140 uniform block of a post-effect pass: `floatCount` is the block's member count,
  * and the buffer is allocated zero-filled at exactly that width.
  */
private[ui] final case class UniformBlock(name: String, floatCount: Int)

/** Installs and writes per-axis uniform buffers for a post chain's single pass, cached per chain
  * instance and re-installed when the chain changes or a buffer was closed. All-or-nothing:
  * [[forChain]] returns `None` unless every block is present, so an asset/code disagreement
  * disables the whole effect instead of half of it.
  */
@Environment(EnvType.CLIENT)
private[ui] final class PostEffectBuffers(blocks: Vector[UniformBlock]) {

  private val usage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE

  private var cachedChain: Option[PostChain] = None
  private var cachedBuffers: Map[String, GpuBuffer] = Map.empty

  /** Per-block uniform buffers for the chain's single pass, installed once per chain instance.
    * Returns `None` unless every block is present.
    */
  def forChain(chain: PostChain): Option[Map[String, GpuBuffer]] = {
    val needsInstall =
      cachedChain.forall(_ ne chain) || cachedBuffers.values.exists(_.isClosed)
    if (needsInstall) {
      cachedChain = Some(chain)
      cachedBuffers = installBuffers(chain)
    }
    if (cachedBuffers.size == blocks.size) Some(cachedBuffers) else None
  }

  /** Maps `values` onto the buffer as std140 floats, in order. */
  def write(buffer: GpuBuffer, values: Vector[Float]): Unit = {
    val view = buffer.map(false, true)
    try {
      values.foldLeft(Std140Builder.intoBuffer(view.data()))((builder, value) =>
        builder.putFloat(value)
      )
    } finally {
      view.close()
    }
  }

  private def installBuffers(chain: PostChain): Map[String, GpuBuffer] = {
    val uniformMap = chain
      .asInstanceOf[PostChainAccessor]
      .casualtiesbelow$getPasses()
      .asScala
      .iterator
      .map(_.asInstanceOf[PostPassAccessor].casualtiesbelow$getCustomUniforms())
      .find(_.containsKey(blocks.head.name))

    uniformMap
      .map { uniforms =>
        blocks.flatMap(block => installBuffer(uniforms, block)).toMap
      }
      .getOrElse {
        CasualtiesBelow.Logger.warn(
          "Post chain {} has no {} uniform buffer; post effects are disabled",
          chain,
          blocks.head.name
        )
        Map.empty
      }
  }

  private def installBuffer(
      uniforms: java.util.Map[String, GpuBuffer],
      block: UniformBlock
  ): Option[(String, GpuBuffer)] =
    if (!uniforms.containsKey(block.name)) {
      CasualtiesBelow.Logger.warn(
        "Post chain pass is missing the {} uniform buffer; that block is disabled",
        block.name
      )
      None
    } else {
      val buffer = createBlockBuffer(block)
      Option(uniforms.put(block.name, buffer)).foreach(_.close())
      Some(block.name -> buffer)
    }

  private def createBlockBuffer(block: UniformBlock): GpuBuffer = {
    val stack = MemoryStack.stackPush()
    try {
      val size =
        (1 to block.floatCount).foldLeft(new Std140SizeCalculator())((calc, _) => calc.putFloat())
      val builder =
        (1 to block.floatCount).foldLeft(Std140Builder.onStack(stack, size.get()))((b, _) =>
          b.putFloat(0.0f)
        )
      RenderSystem
        .getDevice()
        .createBuffer(
          () => s"CasualtiesBelow / post effects / ${block.name}",
          usage,
          builder.get()
        )
    } finally {
      stack.close()
    }
  }
}
