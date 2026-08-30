package dev.krysztal.casualtiesbelow.api.event

import net.minecraft.server.MinecraftServer

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory

/** A successfully installed server gameplay-data generation. */
final class GameplayDataReloadedContext(val server: MinecraftServer)

/** Fired after a successful `/reload`, once the complete candidate generation is installed. Failed
  * reloads keep the prior generation and do not fire this event.
  */
trait GameplayDataReloadedCallback {
  def onGameplayDataReloaded(context: GameplayDataReloadedContext): Unit
}

object GameplayDataReloadedCallback {
  val EVENT: Event[GameplayDataReloadedCallback] = EventFactory.createArrayBacked(
    classOf[GameplayDataReloadedCallback],
    (listeners: Array[GameplayDataReloadedCallback]) =>
      (context: GameplayDataReloadedContext) =>
        listeners.foreach(
          _.onGameplayDataReloaded(context)
        )
  )
}
