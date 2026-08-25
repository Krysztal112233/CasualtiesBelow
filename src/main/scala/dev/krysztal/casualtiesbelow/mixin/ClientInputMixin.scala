package dev.krysztal.casualtiesbelow.mixin

import net.minecraft.client.Minecraft
import net.minecraft.client.player.ClientInput

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

import dev.krysztal.casualtiesbelow.consciousness.Unconsciousness

import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Prevents LocalPlayer's queued auto-jump from restoring jump input after KeyboardInput.tick has
  * cleared the unconscious player's voluntary controls.
  */
@Environment(EnvType.CLIENT)
@Mixin(value = Array(classOf[ClientInput]), remap = false)
abstract class ClientInputMixin {

  @Inject(method = Array("makeJump"), at = Array(new At(value = "HEAD")), cancellable = true)
  private def casualtiesbelow$blockUnconsciousAutoJump(ci: CallbackInfo): Unit = {
    Option(Minecraft.getInstance().player)
      .filter(Unconsciousness.restricts)
      .foreach(_ => ci.cancel())
  }
}
