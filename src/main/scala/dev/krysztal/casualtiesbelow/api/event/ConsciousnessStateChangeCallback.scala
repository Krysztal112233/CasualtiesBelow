package dev.krysztal.casualtiesbelow.api.event

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory

import dev.krysztal.casualtiesbelow.api.body.PainShockStage

/** One server-authoritative transition into or out of unconsciousness. */
final class ConsciousnessStateChangeContext(
    val player: ServerPlayer,
    val previousConsciousness: Double,
    val consciousness: Double,
    val wasUnconscious: Boolean,
    val isUnconscious: Boolean,
    val painShockStage: PainShockStage,
    val cause: Identifier
)

/** Fired after a player actually enters unconsciousness or wakes from it.
  *
  * Component loading, component copying, and fresh construction do not fire this event. It is
  * observational; listeners must not mutate the player's vitals re-entrantly.
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
