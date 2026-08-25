package dev.krysztal.casualtiesbelow.mixin

import scala.compiletime.uninitialized

import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.entity.player.Input

import dev.krysztal.casualtiesbelow.consciousness.Unconsciousness

import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Narrow server-side packet gates for voluntary actions not fully covered by Fabric interaction
  * events.
  *
  * Every gate runs after vanilla's thread handoff. Block and item-use packets are deliberately left
  * to the Fabric callbacks registered by [[Unconsciousness]] so vanilla still acknowledges their
  * sequence numbers and resynchronizes client prediction. Player and vehicle position packets are
  * also left intact: the normal client has no voluntary input, while gravity, knockback, currents,
  * and external vehicle movement must continue. Strict forged-position reconciliation is deferred.
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
        target =
          "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
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
      // Intentionally do not resetLastActionTime: blocked movement must not bypass vanilla's
      // configured AFK timeout.
      ci.cancel()
    }
  }

  @Inject(
    method = Array("handlePlayerAction"),
    at = Array(
      new At(
        value = "INVOKE",
        target =
          "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
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
      case ServerboundPlayerActionPacket.Action.STAB                   => true
      case ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND => true
      case ServerboundPlayerActionPacket.Action.DROP_ITEM              => true
      case ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS         => true
      // Let release and block-break actions reach vanilla. Fabric's interaction callbacks reject
      // mining and preserve vanilla's sequence acknowledgement and block-state resynchronization.
      case _ => false
    }
    if (restricted && blocked) {
      packet.getAction match {
        case ServerboundPlayerActionPacket.Action.DROP_ITEM |
            ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS =>
          // LocalPlayer removes the predicted stack before sending the packet; restore the
          // authoritative inventory after rejecting the drop.
          player.inventoryMenu.sendAllDataToRemote()
        case _ =>
      }
      ci.cancel()
    }
  }

  @Inject(
    method = Array("handlePlayerCommand"),
    at = Array(
      new At(
        value = "INVOKE",
        target =
          "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
        shift = At.Shift.AFTER
      )
    ),
    cancellable = true
  )
  private def casualtiesbelow$blockUnconsciousPlayerCommand(
      packet: ServerboundPlayerCommandPacket,
      ci: CallbackInfo
  ): Unit = {
    val allowed = packet.getAction match {
      case ServerboundPlayerCommandPacket.Action.STOP_SLEEPING    => true
      case ServerboundPlayerCommandPacket.Action.STOP_SPRINTING   => true
      case ServerboundPlayerCommandPacket.Action.STOP_RIDING_JUMP => true
      case ServerboundPlayerCommandPacket.Action.OPEN_INVENTORY   => true
      case _                                                      => false
    }
    if (restricted && !allowed) ci.cancel()
  }

  @Inject(
    method = Array("handlePaddleBoat"),
    at = Array(
      new At(
        value = "INVOKE",
        target =
          "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
        shift = At.Shift.AFTER
      )
    ),
    cancellable = true
  )
  private def casualtiesbelow$blockUnconsciousPaddling(
      packet: ServerboundPaddleBoatPacket,
      ci: CallbackInfo
  ): Unit = {
    if (restricted) ci.cancel()
  }

  @Inject(
    method = Array("handleSetCarriedItem"),
    at = Array(
      new At(
        value = "INVOKE",
        target =
          "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
        shift = At.Shift.AFTER
      )
    ),
    cancellable = true
  )
  private def casualtiesbelow$blockUnconsciousHotbarChange(
      packet: ServerboundSetCarriedItemPacket,
      ci: CallbackInfo
  ): Unit = {
    if (restricted) {
      player.connection.send(
        new ClientboundSetHeldSlotPacket(player.getInventory.getSelectedSlot)
      )
      ci.cancel()
    }
  }

  @Inject(
    method = Array(
      "handleContainerClick",
      "handlePlaceRecipe",
      "handleContainerButtonClick"
    ),
    at = Array(
      new At(
        value = "INVOKE",
        target =
          "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
        shift = At.Shift.AFTER
      )
    ),
    require = 3,
    cancellable = true
  )
  private def casualtiesbelow$blockUnconsciousContainerMutation(ci: CallbackInfo): Unit = {
    if (restricted) {
      player.containerMenu.sendAllDataToRemote()
      ci.cancel()
    }
  }

  private def restricted: Boolean = Unconsciousness.restricts(player)
}
