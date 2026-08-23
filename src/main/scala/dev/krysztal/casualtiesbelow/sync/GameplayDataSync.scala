package dev.krysztal.casualtiesbelow.sync

import java.nio.charset.StandardCharsets

import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking

import dev.krysztal.casualtiesbelow.CasualtiesBelow

import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents
import io.netty.buffer.ByteBuf

/** Carries one [[GameplayDataSnapshot]] JSON document. */
final case class GameplayDataPayload(json: String) extends CustomPacketPayload {
  override def `type`(): CustomPacketPayload.Type[GameplayDataPayload] = GameplayDataSync.PayloadId
}

/** Server→client sync of the effective gameplay data (see [[GameplayDataSnapshot]]).
  *
  * Sent from [[ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS]], which fires per player right before
  * the vanilla tag and recipe packets — both on join (play phase, before the recipe sync that
  * triggers JEI's start) and on every successful datapack reload. This is the canonical hook for
  * datapack→client sync (Fabric PR #2265; NeoForge's OnDatapackSyncEvent shares the semantics):
  * [[ServerLifecycleEvents.END_DATA_PACK_RELOAD]] runs *after* the recipe sync and would leave
  * recipe viewers reading a stale snapshot. Config hot reloads (Forge Config API Port watches the
  * file) rebroadcast to all online players as well.
  */
object GameplayDataSync {

  val PayloadId: CustomPacketPayload.Type[GameplayDataPayload] =
    new CustomPacketPayload.Type(CasualtiesBelow.ofIdentifier("gameplay_data"))

  private val Codec: StreamCodec[ByteBuf, GameplayDataPayload] =
    ByteBufCodecs.BYTE_ARRAY.map(
      bytes => GameplayDataPayload(new String(bytes, StandardCharsets.UTF_8)),
      payload => payload.json.getBytes(StandardCharsets.UTF_8)
    )

  /** The running server, tracked so the (thread-foreign) config reload callback can broadcast. */
  @volatile private var server: MinecraftServer = _

  /** Common-side registration (payload type + send hooks). */
  def register(): Unit = {
    PayloadTypeRegistry.clientboundPlay().register(PayloadId, Codec)

    ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register { (player, _) =>
      send(player)
    }
    ServerLifecycleEvents.SERVER_STARTED.register(s => server = s)
    ServerLifecycleEvents.SERVER_STOPPED.register(_ => server = null)

    // Forge Config API Port hot-reloads the config file in place; rebroadcast so online clients
    // do not keep predicting against stale numbers. The callback runs on the file watcher
    // thread, so hop onto the server thread before capturing or sending.
    ModConfigEvents.reloading(CasualtiesBelow.ModId).register { _ =>
      Option(server).foreach { s =>
        s.execute(() => s.getPlayerList.getPlayers.forEach(send(_)))
      }
    }
  }

  private def send(player: ServerPlayer): Unit = {
    ServerPlayNetworking.send(player, GameplayDataPayload(GameplayDataSnapshot.capture().toJson))
  }

  /** Client-side registration (receiver + disconnect cleanup). */
  def registerClient(): Unit = {
    ClientPlayNetworking.registerGlobalReceiver(
      PayloadId,
      (payload, _) => GameplayDataSnapshot.receive(payload.json)
    )
    ClientPlayConnectionEvents.DISCONNECT.register { (_, _) =>
      GameplayDataSnapshot.clearSynced()
    }
  }
}
