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

  /** Queries a listener for one player's body-heat contribution this tick. Implementations combine
    * the player's own state with the frame's environment snapshot, then write any resulting rate
    * into the context: direct-channel rates for heat that bypasses armor (exercise, fire contact),
    * dissipative-channel rates for cooling that armor may weaken (evaporative cooling).
    *
    * @param player
    *   the player being ticked: the listener's subject and its window onto game state
    * @param frame
    *   this tick's environment snapshot; valid only for the duration of this synchronous dispatch
    * @param context
    *   the write-only accumulator the contribution goes into; do not retain it or call it outside
    *   the callback
    */
  def contribute(
      player: ServerPlayer,
      frame: BodyHeatContributionCallback.Frame,
      context: BodyHeatContributionContext
  ): Unit
}

object BodyHeatContributionCallback {

  /** Per-tick environment handed to listeners alongside the context. Server thread only; do not
    * retain it beyond the dispatch.
    *
    * @param fireResistance
    *   the coverage-weighted armor coefficient against fire contact, deliberately not collapsed by
    *   wetness; heat sources use it to self-mitigate at the source
    * @param wetness
    *   the player's skin wetness (0..1); the strength of evaporative cooling
    * @param airDryness
    *   `1 - biome downfall`, the air's headroom for evaporation: a jungle reads ~0, a desert ~1
    */
  final case class Frame(
      fireResistance: Double,
      wetness: Double,
      airDryness: Double
  )

  val EVENT: Event[BodyHeatContributionCallback] = EventFactory.createArrayBacked(
    classOf[BodyHeatContributionCallback],
    (listeners: Array[BodyHeatContributionCallback]) =>
      (player: ServerPlayer, frame: Frame, context: BodyHeatContributionContext) =>
        listeners.foreach(_.contribute(player, frame, context))
  )

  /** Default mutable context: two accumulators, one per channel. The firing side creates one per
    * dispatch and reads the totals afterwards; listeners must not retain it or call it outside the
    * callback. Not thread-safe: use on the server thread only.
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

    /** Sum of all [[addDirect]] rates since construction. */
    def directTotal: Double = direct

    /** Sum of all [[addDissipative]] rates since construction. */
    def dissipativeTotal: Double = dissipative
  }
}
