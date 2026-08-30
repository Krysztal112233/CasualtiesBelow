package dev.krysztal.casualtiesbelow.api.event

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory

/** One accepted, server-authoritative damage event before adrenaline and wounds are evaluated. This
  * is emitted once per vanilla damage event, never once per wound contribution or limb.
  */
final class TraumaStartedContext(
    val player: ServerPlayer,
    val source: DamageSource,
    val damage: Double,
    val woundsAllowed: Boolean
)

/** Observes a trauma before adrenaline and pain are granted. This event cannot cancel damage. */
trait TraumaStartedCallback {
  def onTraumaStarted(context: TraumaStartedContext): Unit
}

object TraumaStartedCallback {
  val EVENT: Event[TraumaStartedCallback] = EventFactory.createArrayBacked(
    classOf[TraumaStartedCallback],
    (listeners: Array[TraumaStartedCallback]) =>
      (context: TraumaStartedContext) => listeners.foreach(_.onTraumaStarted(context))
  )
}
