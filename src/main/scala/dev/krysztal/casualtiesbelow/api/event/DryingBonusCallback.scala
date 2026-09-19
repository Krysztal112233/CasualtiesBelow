package dev.krysztal.casualtiesbelow.api.event

import net.minecraft.server.level.ServerPlayer

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory

/** Queried every tick for a player's bonus drying temperature (°C).
  *
  * The returned offset feeds only the wetness drying curve (`drying temperature = apparent
  * temperature + Σ bonuses`); it never enters the core body-temperature formula. The built-in
  * source is being on fire; other mods may register their own heat sources here. The event combiner
  * sums all listeners. Fired on the server thread only.
  */
trait DryingBonusCallback {
  def dryingBonus(player: ServerPlayer): Double
}

object DryingBonusCallback {
  val EVENT: Event[DryingBonusCallback] = EventFactory.createArrayBacked(
    classOf[DryingBonusCallback],
    (listeners: Array[DryingBonusCallback]) =>
      (player: ServerPlayer) => {
        var sum = 0.0
        listeners.foreach(listener => sum += listener.dryingBonus(player))
        sum
      }
  )
}
