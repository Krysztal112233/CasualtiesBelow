package dev.krysztal.casualtiesbelow.mixin

import scala.compiletime.uninitialized

import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action as PlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.entity.player.Input
import net.minecraft.world.entity.vehicle.boat.AbstractBoat

import dev.krysztal.casualtiesbelow.physiology.consciousness.Unconsciousness

import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Small set of server-side packet gates for unconscious-player actions that either carry real
  * inventory/vehicle state or commonly race the initial unconscious sync.
  *
  * Ordinary interactions and mining are handled semantically by Fabric callbacks and
  * Player.blockActionRestricted. Low-frequency menu, text, cosmetic, and ability packets are
  * intentionally out of scope. Position packets also remain untouched: suppressing normal-client
  * input is useful hardening, not anti-cheat authority, and server-authoritative reconciliation
  * must not erase gravity, knockback, currents, or external vehicle movement.
  */
@Mixin(value = Array(classOf[ServerGamePacketListenerImpl]), remap = false)
abstract class ServerGamePacketListenerImplMixin {
  @Shadow
  private var player: ServerPlayer = uninitialized

  @Inject(
    method = Array("handlePlayerInput"),
    at = Array(
      new At(
        value = "INVOKE",
        target = ServerGamePacketListenerImplMixin.MainThreadHandoffTarget,
        shift = At.Shift.AFTER
      )
    ),
    cancellable = true
  )
  private def casualtiesbelow$clearUnconsciousInput(
      packet: ServerboundPlayerInputPacket,
      ci: CallbackInfo
  ): Unit = {
    if (restricted) {
      player.setLastClientInput(Input.EMPTY)
      player.setShiftKeyDown(false)
      // NOTE: Movement input can race the unconscious-state sync or come from a modified client.
      // Clearing it keeps the server from reapplying voluntary input, while blocked packets do not
      // refresh vanilla's AFK timer.
      ci.cancel()
    }
  }

  @Inject(
    method = Array("handlePlayerAction"),
    at = Array(
      new At(
        value = "INVOKE",
        target = ServerGamePacketListenerImplMixin.MainThreadHandoffTarget,
        shift = At.Shift.AFTER
      )
    ),
    cancellable = true
  )
  private def casualtiesbelow$blockUnconsciousPlayerAction(
      packet: ServerboundPlayerActionPacket,
      ci: CallbackInfo
  ): Unit = {
    val blocked = packet.getAction match {
      case PlayerActionPacket.SWAP_ITEM_WITH_OFFHAND | PlayerActionPacket.DROP_ITEM |
          PlayerActionPacket.DROP_ALL_ITEMS =>
        true
      // Let release and block-break actions reach vanilla. Fabric's interaction callbacks and
      // Player.blockActionRestricted reject mining while preserving sequence acknowledgement and
      // block-state resynchronization.
      case _ => false
    }
    if (restricted && blocked) {
      packet.getAction match {
        case PlayerActionPacket.DROP_ITEM | PlayerActionPacket.DROP_ALL_ITEMS =>
          // NOTE: LocalPlayer removes the predicted stack before sending a drop packet, so the
          // authoritative inventory must be resent after rejecting the drop. Offhand swap has no
          // such local pre-send mutation and needs no rollback.
          player.inventoryMenu.sendAllDataToRemote()
        case _ =>
      }
      ci.cancel()
    }
  }

  @Inject(
    method = Array("handlePaddleBoat"),
    at = Array(
      new At(
        value = "INVOKE",
        target = ServerGamePacketListenerImplMixin.MainThreadHandoffTarget,
        shift = At.Shift.AFTER
      )
    ),
    cancellable = true
  )
  private def casualtiesbelow$blockUnconsciousPaddling(
      packet: ServerboundPaddleBoatPacket,
      ci: CallbackInfo
  ): Unit = {
    if (restricted) {
      player.getControlledVehicle match {
        // NOTE: Cancelling a paddle packet alone leaves any previously synchronized paddle flags
        // enabled, so the boat would continue moving without a way for the unconscious player to
        // stop it. Reset both flags before rejecting the packet.
        case boat: AbstractBoat => boat.setPaddleState(false, false)
        case _                  =>
      }
      ci.cancel()
    }
  }

  @Inject(
    method = Array("handleContainerClick"),
    at = Array(
      new At(
        value = "INVOKE",
        target = ServerGamePacketListenerImplMixin.MainThreadHandoffTarget,
        shift = At.Shift.AFTER
      )
    ),
    cancellable = true
  )
  private def casualtiesbelow$blockUnconsciousContainerClick(
      packet: ServerboundContainerClickPacket,
      ci: CallbackInfo
  ): Unit = rejectContainerMutation(packet.containerId(), ci)

  private def rejectContainerMutation(containerId: Int, ci: CallbackInfo): Unit = {
    if (restricted) {
      // NOTE: Container clicks may already be in flight when the player becomes unconscious.
      // Reject only the matching active menu; a stale or forged container id must not trigger a
      // full synchronization of the player's current menu.
      if (player.containerMenu.containerId == containerId) {
        player.containerMenu.sendAllDataToRemote()
      }
      ci.cancel()
    }
  }

  private def restricted: Boolean = Unconsciousness.restricts(player)
}

object ServerGamePacketListenerImplMixin {

  /** Single source for the vanilla server-thread handoff descriptor used by all synchronous packet
    * gates. Keeping the long JVM signature in one place avoids partial updates or typos.
    */
  private final val MainThreadHandoffTarget =
    "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"
}
