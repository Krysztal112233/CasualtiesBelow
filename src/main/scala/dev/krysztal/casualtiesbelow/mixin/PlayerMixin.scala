package dev.krysztal.casualtiesbelow.mixin

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level

import dev.krysztal.casualtiesbelow.physiology.consciousness.Unconsciousness

import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** Makes vanilla's immobility path clear voluntary movement intent for an unconscious player while
  * leaving gravity, knockback, currents, vehicles, and other external motion active. The block
  * restriction hook gives both client and server mining code a direct, prediction-aware gate.
  */
@Mixin(value = Array(classOf[Player]), remap = false)
abstract class PlayerMixin {

  @Inject(method = Array("isImmobile"), at = Array(new At(value = "RETURN")), cancellable = true)
  private def casualtiesbelow$unconsciousIsImmobile(cir: CallbackInfoReturnable[Boolean]): Unit = {
    if (Unconsciousness.restricts(this.asInstanceOf[Player])) {
      cir.setReturnValue(true)
    }
  }

  @Inject(
    method = Array("blockActionRestricted"),
    at = Array(new At(value = "RETURN")),
    cancellable = true
  )
  private def casualtiesbelow$restrictUnconsciousBlockActions(
      level: Level,
      pos: BlockPos,
      gameType: GameType,
      cir: CallbackInfoReturnable[Boolean]
  ): Unit = {
    if (Unconsciousness.restricts(this.asInstanceOf[Player])) {
      cir.setReturnValue(true)
    }
  }
}
