package dev.krysztal.casualtiesbelow.api.event

import net.minecraft.server.level.ServerPlayer

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory

/** Collects per-tick body-heat contributions on the production pathway of the body-temperature
  * system. Listeners declare which channel each contribution belongs to; the temperature loop
  * applies armor checks to the dissipative channel only.
  */
trait BodyHeatContributionContext {

  /** Adds a heating/cooling rate (°C per minute) applied directly to core body temperature,
    * bypassing all armor checks (exercise heat, fire/lava contact heat). Positive heats, negative
    * cools. Source-specific mitigation (e.g. netherite fire resistance) is applied by the
    * contributing listener itself, before calling this method.
    */
  def addDirect(ratePerMinute: Double): Unit

  /** Adds a dissipation rate (°C per minute) multiplied by `(1 - R)`, where `R` is the armor
    * dissipation-block coefficient, before being applied (evaporative cooling, overheated
    * re-dissipation). Positive cools; heating contributions must use [[addDirect]] instead.
    */
  def addDissipative(ratePerMinute: Double): Unit
}

/** Queried every tick for a player's heat contributions on the production pathway.
  *
  * Listeners write into the shared [[BodyHeatContributionContext]]; they must not retain it, call
  * it outside the callback, or re-fire the event with it. The context is created and reset by the
  * firing side — the event combiner never resets it — so an implementation may be reused across
  * ticks only by the firing side. Fired on the server thread only; not thread-safe.
  */
trait BodyHeatContributionCallback {
  def contribute(player: ServerPlayer, context: BodyHeatContributionContext): Unit
}

object BodyHeatContributionCallback {
  val EVENT: Event[BodyHeatContributionCallback] = EventFactory.createArrayBacked(
    classOf[BodyHeatContributionCallback],
    (listeners: Array[BodyHeatContributionCallback]) =>
      (player: ServerPlayer, context: BodyHeatContributionContext) =>
        listeners.foreach(_.contribute(player, context))
  )

  /** Default mutable context: two accumulators, one per channel. Create one per invocation, or
    * reuse an instance across ticks by calling [[reset]] before each use. Not thread-safe: use on
    * the server thread only.
    */
  final class Accumulator extends BodyHeatContributionContext {
    private var direct: Double = 0.0
    private var dissipative: Double = 0.0

    override def addDirect(ratePerMinute: Double): Unit = {
      direct += ratePerMinute
    }

    override def addDissipative(ratePerMinute: Double): Unit = {
      dissipative += ratePerMinute
    }

    /** Sum of all [[addDirect]] rates since construction or the last [[reset]]. */
    def directTotal: Double = direct

    /** Sum of all [[addDissipative]] rates since construction or the last [[reset]]. */
    def dissipativeTotal: Double = dissipative

    /** Clears both accumulators so this instance can be reused for the next invocation. */
    def reset(): Unit = {
      direct = 0.0
      dissipative = 0.0
    }
  }
}
