package dev.krysztal.casualtiesbelow.physiology.temperature

import java.util.UUID

import scala.collection.mutable

import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extension.FoodDataExtensions.*

/** Shared exertion signal: vanilla's exhaustion bookkeeping already prices each activity, so its
  * per-tick delta is the signal. The periodic 4.0 hunger-billing drain shows up as a negative delta
  * and is truncated (an accounting artifact, not negative exercise). The delta is smoothed with an
  * exponential moving average over roughly five seconds so single actions (a jump, an attack)
  * register as brief exertion instead of one-tick spikes. Used by the exercise-heat and sweating
  * listeners; [[discard]] clears a player's state.
  */
private[temperature] object ExertionTracker {

  /** Smoothed exhaustion rate in units per second; 0 before any exertion is observed. */
  def exhaustionPerSecond(id: UUID): Double =
    tracked.get(id).fold(0.0)(_.smoothedPerTick * Consts.TicksPerSecond)

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
