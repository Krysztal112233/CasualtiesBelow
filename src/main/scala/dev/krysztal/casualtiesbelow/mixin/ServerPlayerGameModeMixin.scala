package dev.krysztal.casualtiesbelow.mixin

import scala.compiletime.uninitialized

import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerPlayerGameMode
import net.minecraft.world.level.GameType

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.component.BodyMutations

import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** Reconciles limb-derived movement modifiers immediately after a runtime game-mode transition.
  * Creative and spectator suppress them while survival and adventure restore them from the retained
  * limb state, without waiting for that state to change.
  */
@Mixin(value = Array(classOf[ServerPlayerGameMode]), remap = false)
abstract class ServerPlayerGameModeMixin {
  @Shadow
  @Final
  private var player: ServerPlayer = uninitialized

  @Inject(
    method = Array("changeGameModeForPlayer"),
    at = Array(new At(value = "RETURN")),
    remap = false
  )
  private def casualtiesbelow$reconcileLimbMovement(
      gameType: GameType,
      cir: CallbackInfoReturnable[Boolean]
  ): Unit = {
    if (cir.getReturnValue) {
      BodyMutations.reconcileMovementModifiers(player)
    }
  }
}
