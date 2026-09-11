package dev.krysztal.casualtiesbelow.mixin

import net.minecraft.client.Minecraft
import net.minecraft.client.player.ClientInput
import net.minecraft.client.player.KeyboardInput
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec2

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

import dev.krysztal.casualtiesbelow.physiology.consciousness.Unconsciousness

import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Immediately suppresses voluntary keyboard input for an unconscious local player. Server gates
  * remain authoritative; this only keeps a normal client responsive and avoids useless packets.
  */
@Environment(EnvType.CLIENT)
@Mixin(value = Array(classOf[KeyboardInput]), remap = false)
abstract class KeyboardInputMixin extends ClientInput {

  @Inject(method = Array("tick"), at = Array(new At(value = "TAIL")))
  private def casualtiesbelow$suppressUnconsciousInput(ci: CallbackInfo): Unit = {
    Option(Minecraft.getInstance().player).filter(Unconsciousness.restricts).foreach { _ =>
      keyPresses = Input.EMPTY
      moveVector = Vec2.ZERO
    }
  }
}
