package dev.krysztal.casualtiesbelow.mixin

import net.minecraft.world.entity.player.Player

import dev.krysztal.casualtiesbelow.consciousness.Unconsciousness

import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** Makes vanilla's immobility path clear voluntary movement intent for an unconscious player while
  * leaving gravity, knockback, currents, vehicles, and other external motion active.
  */
@Mixin(value = Array(classOf[Player]), remap = false)
abstract class PlayerMixin {

  @Inject(method = Array("isImmobile"), at = Array(new At(value = "HEAD")), cancellable = true)
  private def casualtiesbelow$unconsciousIsImmobile(cir: CallbackInfoReturnable[Boolean]): Unit = {
    val player = this.asInstanceOf[Player]
    if (!player.level().isClientSide() && Unconsciousness.restricts(player)) {
      cir.setReturnValue(true)
    }
  }
}
