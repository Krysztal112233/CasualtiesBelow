package dev.krysztal.casualtiesbelow.internal.sync

import java.lang.Boolean as JBoolean
import java.lang.Double as JDouble
import java.lang.Long as JLong

import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.world.InteractionHand

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.item.InjectionSettlement

import io.netty.buffer.ByteBuf

/** One batched injection progress report, sent client→server while the injection screen is pushing.
  * `droplets` are whole droplets pushed since the previous batch (so the running total can never
  * exceed the syringe's contents); `averageSpeed` is the droplet-weighted average push speed as a
  * fraction of the configured maximum. `liquid` and `expectedDroplets` identify the syringe the
  * screen session belongs to (the liquid drawn and the droplets the stack should hold once all
  * previously flushed batches have settled): the server ignores a batch whose identity does not
  * match the held stack, so swapping the held syringe mid-session cannot redirect settlement onto
  * another stack.
  */
final case class InjectionBatchPayload(
    mainHand: Boolean,
    liquid: Identifier,
    expectedDroplets: Long,
    droplets: Long,
    averageSpeed: Double
) extends CustomPacketPayload {
  override def `type`(): CustomPacketPayload.Type[InjectionBatchPayload] = InjectionSync.PayloadId
}

/** Client→server injection progress channel.
  *
  * Trust model: this mod targets single-player, so the client reports minigame outcomes instead of
  * streaming raw input; the server (see [[dev.krysztal.casualtiesbelow.item.InjectionSettlement]])
  * clamps every report to the syringe's actual remainder and the speed cap rather than
  * reconstructing the minigame. The receiver runs on the server thread (Fabric guarantees this for
  * play-phase global receivers).
  */
object InjectionSync {

  val PayloadId: CustomPacketPayload.Type[InjectionBatchPayload] =
    new CustomPacketPayload.Type(CasualtiesBelow.ofIdentifier("injection_batch"))

  private[casualtiesbelow] val Codec: StreamCodec[ByteBuf, InjectionBatchPayload] =
    StreamCodec.composite(
      ByteBufCodecs.BOOL,
      (payload: InjectionBatchPayload) => JBoolean.valueOf(payload.mainHand),
      Identifier.STREAM_CODEC,
      (payload: InjectionBatchPayload) => payload.liquid,
      ByteBufCodecs.LONG,
      (payload: InjectionBatchPayload) => JLong.valueOf(payload.expectedDroplets),
      ByteBufCodecs.LONG,
      (payload: InjectionBatchPayload) => JLong.valueOf(payload.droplets),
      ByteBufCodecs.DOUBLE,
      (payload: InjectionBatchPayload) => JDouble.valueOf(payload.averageSpeed),
      (
          mainHand: JBoolean,
          liquid: Identifier,
          expectedDroplets: JLong,
          droplets: JLong,
          averageSpeed: JDouble
      ) =>
        InjectionBatchPayload(
          mainHand.booleanValue(),
          liquid,
          expectedDroplets.longValue(),
          droplets.longValue(),
          averageSpeed.doubleValue()
        )
    )

  /** Common-side registration (payload type + server receiver). The client needs no receiver: it
    * only sends, and remainder write-backs arrive through vanilla inventory sync.
    */
  def register(): Unit = {
    PayloadTypeRegistry.serverboundPlay().register(PayloadId, Codec)
    ServerPlayNetworking.registerGlobalReceiver(
      PayloadId,
      (payload, context) =>
        InjectionSettlement.applyBatch(
          context.player(),
          if (payload.mainHand) InteractionHand.MAIN_HAND else InteractionHand.OFF_HAND,
          payload.liquid,
          payload.expectedDroplets,
          payload.droplets,
          payload.averageSpeed
        )
    )
  }
}
