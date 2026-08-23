package dev.krysztal.casualtiesbelow.sync

import java.nio.charset.StandardCharsets

import scala.jdk.CollectionConverters.*

import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking

import dev.krysztal.casualtiesbelow.CasualtiesBelow

import io.netty.buffer.ByteBuf

/** Carries one [[GameplayDataSnapshot]] JSON document. */
final case class GameplayDataPayload(json: String) extends CustomPacketPayload {
  override def `type`(): CustomPacketPayload.Type[GameplayDataPayload] = GameplayDataSync.PayloadId
}

/** Server→client sync of the effective gameplay data (see [[GameplayDataSnapshot]]).
  *
  * The snapshot is sent during the configuration phase on join — datapacks are already loaded on
  * the server by then, and the payload lands before the recipe sync that triggers JEI's start, so
  * recipe viewers see the server's numbers from the first frame — and again in the play phase after
  * every successful datapack reload.
  */
object GameplayDataSync {

  val PayloadId: CustomPacketPayload.Type[GameplayDataPayload] =
    new CustomPacketPayload.Type(CasualtiesBelow.ofIdentifier("gameplay_data"))

  private val Codec: StreamCodec[ByteBuf, GameplayDataPayload] =
    ByteBufCodecs.BYTE_ARRAY.map(
      bytes => GameplayDataPayload(new String(bytes, StandardCharsets.UTF_8)),
      payload => payload.json.getBytes(StandardCharsets.UTF_8)
    )

  private def payload(): GameplayDataPayload = {
    GameplayDataPayload(GameplayDataSnapshot.capture().toJson)
  }

  /** Common-side registration (payload types + send hooks). */
  def register(): Unit = {
    PayloadTypeRegistry.clientboundConfiguration().register(PayloadId, Codec)
    PayloadTypeRegistry.clientboundPlay().register(PayloadId, Codec)

    ServerConfigurationConnectionEvents.CONFIGURE.register { (listener, _) =>
      ServerConfigurationNetworking.send(listener, payload())
    }
    ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { (server, _, success) =>
      if (success) {
        server.getPlayerList.getPlayers.forEach { player =>
          ServerPlayNetworking.send(player, payload())
        }
      }
    }
  }

  /** Client-side registration (receivers + disconnect cleanup). */
  def registerClient(): Unit = {
    ClientConfigurationNetworking.registerGlobalReceiver(
      PayloadId,
      (payload, _) => GameplayDataSnapshot.receive(payload.json)
    )
    ClientPlayNetworking.registerGlobalReceiver(
      PayloadId,
      (payload, _) => GameplayDataSnapshot.receive(payload.json)
    )
    ClientPlayConnectionEvents.DISCONNECT.register { (_, _) =>
      GameplayDataSnapshot.clearSynced()
    }
  }
}
