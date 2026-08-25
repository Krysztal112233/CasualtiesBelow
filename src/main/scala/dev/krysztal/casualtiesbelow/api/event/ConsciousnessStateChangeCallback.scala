package dev.krysztal.casualtiesbelow.api.event

import net.minecraft.server.level.ServerPlayer

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory

/** Context for one server-authoritative unconsciousness transition.
  *
  * Carried as an object so future transition metadata can be added without changing listener
  * signatures. This event is observational and cannot cancel the physiological state change.
  */
final case class ConsciousnessStateChangeContext(
    player: ServerPlayer,
    wasUnconscious: Boolean,
    isUnconscious: Boolean,
    consciousness: Double
)

/** Fired once after a player actually enters unconsciousness or wakes from it.
  *
  * Server-side only. Ordinary component loading, lossless copying, and fresh death-respawn
  * construction do not fire this event. Explicit recovery resets do fire when they wake the player.
  * Listeners must not mutate the player's vitals component re-entrantly.
  */
trait ConsciousnessStateChangeCallback {
  def onConsciousnessStateChange(context: ConsciousnessStateChangeContext): Unit
}

object ConsciousnessStateChangeCallback {
  val EVENT: Event[ConsciousnessStateChangeCallback] = EventFactory.createArrayBacked(
    classOf[ConsciousnessStateChangeCallback],
    (listeners: Array[ConsciousnessStateChangeCallback]) =>
      (context: ConsciousnessStateChangeContext) =>
        listeners.foreach(_.onConsciousnessStateChange(context))
  )
}
