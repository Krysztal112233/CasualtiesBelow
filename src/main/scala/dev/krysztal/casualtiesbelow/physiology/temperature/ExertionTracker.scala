package dev.krysztal.casualtiesbelow.physiology.temperature

import java.util.UUID

import scala.collection.mutable

import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*

/** Turns vanilla exhaustion deltas into the exertion signal for exercise heat.
  *
  * Hunger-billing drops are ignored; a ~5-second EMA smooths activity. [[discard]] clears player
  * state.
  */
private[temperature] object ExertionTracker {

  def observe(player: ServerPlayer): Double = {
    val exhaustion = player.getFoodData.exhaustionLevel
    val track = tracked.getOrElseUpdate(player.getUUID, new Track(exhaustion))
    val delta = (exhaustion - track.lastExhaustion).toDouble.max(0.0)
    track.lastExhaustion = exhaustion
    track.smoothedPerTick += (delta - track.smoothedPerTick) * SmoothingAlpha
    track.smoothedPerTick * Consts.TicksPerSecond
  }

  def discard(id: UUID): Unit = tracked.remove(id)

  private final class Track(var lastExhaustion: Float) {
    var smoothedPerTick: Double = 0.0
  }

  private val tracked = mutable.Map.empty[UUID, Track]

  /** EMA weight for a ~5 second (100 tick) window. */
  private val SmoothingAlpha = 2.0 / (100.0 + 1.0)
}
