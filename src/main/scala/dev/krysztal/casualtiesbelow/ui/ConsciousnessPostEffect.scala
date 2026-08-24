package dev.krysztal.casualtiesbelow.ui

import scala.jdk.CollectionConverters.*

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.resource.CrossFrameResourcePool
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.LevelTargetBundle
import net.minecraft.client.renderer.PostChain
import net.minecraft.util.Mth

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.mixin.PostChainAccessor
import dev.krysztal.casualtiesbelow.mixin.PostPassAccessor

import org.lwjgl.system.MemoryStack

/** Shader-backed dimming at low consciousness. It preserves the old HUD overlay's threshold, pulse,
  * weaker center haze and stronger edge darkening, but now applies them to the world through a post
  * pass before the GUI is rendered.
  */
@Environment(EnvType.CLIENT)
object ConsciousnessPostEffect {
  private val ChainId = CasualtiesBelow.ofIdentifier("consciousness_vignette")
  private val UniformGroup = "ConsciousnessConfig"
  private val UniformBufferSize = new Std140SizeCalculator().putFloat().get()
  private val UniformBufferUsage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE
  private val PulseSpeed = (2.0 * Math.PI / 40.0).toFloat

  private var cachedChain: Option[PostChain] = None
  private var cachedStrengthBuffer: Option[GpuBuffer] = None

  def render(
      gameRenderer: GameRenderer,
      deltaTracker: DeltaTracker,
      resourcePool: CrossFrameResourcePool
  ): Unit = {
    strength(deltaTracker).filter(_ > 0.0f).foreach { value =>
      Option(
        Minecraft
          .getInstance()
          .getShaderManager()
          .getPostChain(ChainId, LevelTargetBundle.MAIN_TARGETS)
      ).foreach { chain =>
        strengthBuffer(chain).foreach { buffer =>
          writeStrength(buffer, value)
          chain.process(gameRenderer.mainRenderTarget(), resourcePool)
        }
      }
    }
  }

  private def strength(deltaTracker: DeltaTracker): Option[Float] = {
    Option(Minecraft.getInstance().player)
      .filter(_ => !Minecraft.getInstance().gui.hud.isHidden())
      .filter(player => !player.isSpectator && player.isAlive)
      .flatMap { player =>
        val maxOpacity = CasualtiesBelowConfig.ConsciousnessMaxDimOpacity.get().toFloat
        val dim = CasualtiesBelowConfig.ConsciousnessDimThreshold.get()
        val blackout = math.min(CasualtiesBelowConfig.ConsciousnessBlackoutThreshold.get(), dim)
        val consciousness = CasualtiesBelowComponents.Vitals.get(player).consciousness
        if (maxOpacity <= 0.0f || consciousness >= dim) {
          None
        } else {
          var value = ((dim - consciousness) / (dim - blackout).max(1.0e-6)).toFloat
          value = Mth.clamp(value, 0.0f, 1.0f)
          if (consciousness <= blackout) {
            val time = player.tickCount + deltaTracker.getGameTimeDeltaPartialTick(true)
            value *= 0.85f + 0.15f * Mth.sin(time * PulseSpeed)
          }
          Some(maxOpacity * value)
        }
      }
  }

  private def strengthBuffer(chain: PostChain): Option[GpuBuffer] = {
    val needsInstall = cachedChain.forall(_ ne chain) || cachedStrengthBuffer.exists(_.isClosed)
    if (needsInstall) {
      cachedChain = Some(chain)
      cachedStrengthBuffer = installStrengthBuffer(chain)
    }
    cachedStrengthBuffer
  }

  private def installStrengthBuffer(chain: PostChain): Option[GpuBuffer] = {
    val uniformMap = chain
      .asInstanceOf[PostChainAccessor]
      .casualtiesbelow$getPasses()
      .asScala
      .iterator
      .map(_.asInstanceOf[PostPassAccessor].casualtiesbelow$getCustomUniforms())
      .find(_.containsKey(UniformGroup))

    uniformMap
      .map { uniforms =>
        val buffer = createStrengthBuffer()
        Option(uniforms.put(UniformGroup, buffer)).foreach(_.close())
        buffer
      }
      .orElse {
        CasualtiesBelow.Logger.warn(
          "Post chain {} has no {} uniform buffer; consciousness dimming is disabled",
          ChainId,
          UniformGroup
        )
        None
      }
  }

  private def createStrengthBuffer(): GpuBuffer = {
    val stack = MemoryStack.stackPush()
    try {
      val builder = Std140Builder.onStack(stack, UniformBufferSize)
      builder.putFloat(0.0f)
      RenderSystem
        .getDevice()
        .createBuffer(
          () => "CasualtiesBelow / consciousness strength",
          UniformBufferUsage,
          builder.get()
        )
    } finally {
      stack.close()
    }
  }

  private def writeStrength(buffer: GpuBuffer, value: Float): Unit = {
    val view = buffer.map(false, true)
    try {
      Std140Builder.intoBuffer(view.data()).putFloat(value)
    } finally {
      view.close()
    }
  }
}
