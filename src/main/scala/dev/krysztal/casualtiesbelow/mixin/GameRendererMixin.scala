package dev.krysztal.casualtiesbelow.mixin

import scala.compiletime.uninitialized

import com.mojang.blaze3d.resource.CrossFrameResourcePool
import net.minecraft.client.DeltaTracker
import net.minecraft.client.renderer.GameRenderer

import dev.krysztal.casualtiesbelow.ui.VitalsPostEffect

import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Applies the mod's full-screen post effect after vanilla world/entity post processing and before
  * the GUI is rendered, so HUD elements remain sharp and unobscured.
  */
@Mixin(value = Array(classOf[GameRenderer]), remap = false)
abstract class GameRendererMixin {
  @Shadow
  @Final
  private var resourcePool: CrossFrameResourcePool = uninitialized

  @Inject(
    method = Array("render"),
    at = Array(
      new At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V",
        shift = At.Shift.BEFORE
      )
    ),
    remap = false
  )
  private def casualtiesbelow$renderVitalsEffect(
      deltaTracker: DeltaTracker,
      advanceGameTime: Boolean,
      ci: CallbackInfo
  ): Unit = {
    VitalsPostEffect.render(
      this.asInstanceOf[GameRenderer],
      deltaTracker,
      resourcePool
    )
  }
}
