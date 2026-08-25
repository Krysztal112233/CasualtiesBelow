package dev.krysztal.casualtiesbelow.mixin

import java.util.List as JavaList

import scala.compiletime.uninitialized

import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.FilteredText
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.entity.player.Input
import net.minecraft.world.entity.vehicle.boat.AbstractBoat
import net.minecraft.world.level.block.entity.SignBlockEntity

import dev.krysztal.casualtiesbelow.consciousness.Unconsciousness

import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Narrow server-side packet gates for voluntary actions not fully covered by Fabric interaction
  * events.
  *
  * Every synchronous gate runs after vanilla's thread handoff. Block and item-use packets are
  * deliberately left to the Fabric callbacks registered by [[Unconsciousness]] so vanilla still
  * acknowledges their sequence numbers and resynchronizes client prediction. Player and vehicle
  * position packets are also left intact: suppressing normal-client input is useful hardening, not
  * anti-cheat authority, and server-authoritative reconciliation must not erase gravity, knockback,
  * currents, or external vehicle movement.
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
      // Let release and block-break actions reach vanilla. Fabric's interaction callbacks and
      // Player.blockActionRestricted reject mining while preserving sequence acknowledgement and
      // block-state resynchronization.
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
    if (restricted) {
      player.getControlledVehicle match {
        case boat: AbstractBoat => boat.setPaddleState(false, false)
        case _                  =>
      }
      ci.cancel()
    }
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
    method = Array("handleAnimate"),
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
  private def casualtiesbelow$blockUnconsciousSwing(
      packet: ServerboundSwingPacket,
      ci: CallbackInfo
  ): Unit = {
    if (restricted) ci.cancel()
  }

  @Inject(
    method = Array(
      "handleBundleItemSelectedPacket",
      "handleRenameItem",
      "handleSetBeaconPacket",
      "handleSelectTrade"
    ),
    at = Array(
      new At(
        value = "INVOKE",
        target =
          "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
        shift = At.Shift.AFTER
      )
    ),
    require = 4,
    cancellable = true
  )
  private def casualtiesbelow$blockUnconsciousMenuMutation(ci: CallbackInfo): Unit = {
    if (restricted) {
      player.containerMenu.sendAllDataToRemote()
      ci.cancel()
    }
  }

  @Inject(
    method = Array("handleContainerClick"),
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
  private def casualtiesbelow$blockUnconsciousContainerClick(
      packet: ServerboundContainerClickPacket,
      ci: CallbackInfo
  ): Unit = rejectContainerMutation(packet.containerId(), ci)

  @Inject(
    method = Array("handlePlaceRecipe"),
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
  private def casualtiesbelow$blockUnconsciousRecipePlacement(
      packet: ServerboundPlaceRecipePacket,
      ci: CallbackInfo
  ): Unit = rejectContainerMutation(packet.containerId(), ci)

  @Inject(
    method = Array("handleContainerButtonClick"),
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
  private def casualtiesbelow$blockUnconsciousContainerButton(
      packet: ServerboundContainerButtonClickPacket,
      ci: CallbackInfo
  ): Unit = rejectContainerMutation(packet.containerId(), ci)

  @Inject(
    method = Array("handleContainerSlotStateChanged"),
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
  private def casualtiesbelow$blockUnconsciousCrafterSlotState(
      packet: ServerboundContainerSlotStateChangedPacket,
      ci: CallbackInfo
  ): Unit = rejectContainerMutation(packet.containerId(), ci)

  @Inject(
    method = Array("handlePlayerAbilities"),
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
  private def casualtiesbelow$blockUnconsciousAbilitiesChange(
      packet: ServerboundPlayerAbilitiesPacket,
      ci: CallbackInfo
  ): Unit = {
    if (restricted) {
      player.getAbilities.flying = false
      player.onUpdateAbilities()
      ci.cancel()
    }
  }

  // Book text filtering is asynchronous. Checking only handleEditBook would leave a race where the
  // player becomes unconscious before the filtered text is finally committed on the server thread.
  @Inject(
    method = Array("updateBookContents", "signBook"),
    at = Array(new At(value = "HEAD")),
    require = 2,
    cancellable = true
  )
  private def casualtiesbelow$blockUnconsciousBookMutation(ci: CallbackInfo): Unit = {
    if (restricted) {
      player.inventoryMenu.sendAllDataToRemote()
      ci.cancel()
    }
  }

  // Sign filtering has the same asynchronous boundary as books. Restore the authoritative block
  // entity too, because the edit screen mutates its client-side copy before sending the packet.
  @Inject(method = Array("updateSignText"), at = Array(new At(value = "HEAD")), cancellable = true)
  private def casualtiesbelow$blockUnconsciousSignMutation(
      packet: ServerboundSignUpdatePacket,
      lines: JavaList[FilteredText],
      ci: CallbackInfo
  ): Unit = {
    if (restricted) {
      player.level().getBlockEntity(packet.getPos) match {
        case sign: SignBlockEntity =>
          Option(sign.getUpdatePacket).foreach(player.connection.send)
        case _ =>
      }
      ci.cancel()
    }
  }

  private def rejectContainerMutation(containerId: Int, ci: CallbackInfo): Unit = {
    if (restricted) {
      // Never synchronize the active menu in response to a stale or forged container id.
      if (player.containerMenu.containerId == containerId) {
        player.containerMenu.sendAllDataToRemote()
      }
      ci.cancel()
    }
  }

  private def restricted: Boolean = Unconsciousness.restricts(player)
}
