package dev.krysztal.casualtiesbelow.consciousness

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Input
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult

import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents

/** Query and enforcement entry points for the server-authored unconscious latch.
  *
  * Restrictions deliberately cover voluntary actions only. Vanilla damage, air, gravity, currents,
  * knockback, passengers, portals, and all physiological progression remain untouched. Creative and
  * spectator players bypass restrictions without clearing their stored condition.
  */
object Unconsciousness {

  def restricts(player: Player): Boolean = {
    player.isAlive &&
    !player.isCreative &&
    !player.isSpectator &&
    CasualtiesBelowComponents.Vitals.get(player).unconscious
  }

  def register(): Unit = {
    AttackBlockCallback.EVENT.register(
      (player: Player, _: Level, _: InteractionHand, _: BlockPos, _: Direction) =>
        interactionResult(player)
    )
    AttackEntityCallback.EVENT.register(
      (
          player: Player,
          _: Level,
          _: InteractionHand,
          _: Entity,
          _: EntityHitResult | Null
      ) => interactionResult(player)
    )
    UseBlockCallback.EVENT.register(
      (player: Player, _: Level, _: InteractionHand, _: BlockHitResult) => interactionResult(player)
    )
    UseItemCallback.EVENT.register((player: Player, _: Level, _: InteractionHand) =>
      interactionResult(player)
    )
    UseEntityCallback.EVENT.register(
      (
          player: Player,
          _: Level,
          _: InteractionHand,
          _: Entity,
          _: EntityHitResult | Null
      ) => interactionResult(player)
    )
    PlayerBlockBreakEvents.BEFORE.register((_, player, _, _, _) => !restricts(player))
  }

  /** Clears active voluntary actions exactly when unconsciousness begins. The player remains in a
    * vehicle and can still be moved by the world or another controller.
    */
  def onEntered(player: ServerPlayer): Unit = {
    player.stopUsingItem()
    player.setSprinting(false)
    player.stopFallFlying()
    player.setShiftKeyDown(false)
    player.setLastClientInput(Input.EMPTY)
    if (player.isSleeping) {
      player.stopSleepInBed(false, true)
    }
  }

  private def interactionResult(player: Player): InteractionResult = {
    if (restricts(player)) InteractionResult.FAIL else InteractionResult.PASS
  }
}
