package dev.krysztal.casualtiesbelow.api.event

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory

/** One actual change to a player's server-authoritative adrenaline reserve. */
final class AdrenalineChangedContext(
    val player: ServerPlayer,
    val previousAmount: Double,
    val amount: Double,
    val cause: Identifier
) {
  val delta: Double = amount - previousAmount
}

/** Fired after the adrenaline amount changes. Hidden grace-only changes do not fire it. */
trait AdrenalineChangedCallback {
  def onAdrenalineChanged(context: AdrenalineChangedContext): Unit
}

object AdrenalineChangedCallback {
  val EVENT: Event[AdrenalineChangedCallback] = EventFactory.createArrayBacked(
    classOf[AdrenalineChangedCallback],
    (listeners: Array[AdrenalineChangedCallback]) =>
      (context: AdrenalineChangedContext) => listeners.foreach(_.onAdrenalineChanged(context))
  )
}
