package dev.krysztal.casualtiesbelow.api.event

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory

import dev.krysztal.casualtiesbelow.api.body.vitals.PainShockStage

/** One committed transition in the pain-shock state machine. */
final class PainShockStageChangedContext(
    val player: ServerPlayer,
    val previousStage: PainShockStage,
    val stage: PainShockStage,
    val previousLoad: Double,
    val load: Double,
    val cause: Identifier
)

/** Fired after the pain-shock stage changes. Pure load changes do not fire it. */
trait PainShockStageChangedCallback {
  def onPainShockStageChanged(context: PainShockStageChangedContext): Unit
}

object PainShockStageChangedCallback {
  val EVENT: Event[PainShockStageChangedCallback] = EventFactory.createArrayBacked(
    classOf[PainShockStageChangedCallback],
    (listeners: Array[PainShockStageChangedCallback]) =>
      (context: PainShockStageChangedContext) =>
        listeners.foreach(
          _.onPainShockStageChanged(context)
        )
  )
}
