package dev.krysztal.casualtiesbelow.api.event

import net.minecraft.server.level.ServerPlayer

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory

/** Collects per-tick contributions on the skin-wetness axis. Wetting contributions are positive,
  * drying contributions negative; the wetness loop sums them into a single signed rate.
  */
trait WetnessContributionContext {

  /** Adds a wetness rate (axis points per second) to this tick's total; the axis spans 0 (dry) to
    * 100 (soaked). Positive wets (immersion, rain, sweat), negative dries (environmental drying,
    * fire flash-dry).
    */
  def add(ratePerSecond: Double): Unit
}

/** Queried every tick for a player's wetness contributions.
  *
  * Listeners write into the shared [[WetnessContributionContext]]; they must not retain it, call it
  * outside the callback, or re-fire the event with it. The context is created and reset by the
  * firing side — the event combiner never resets it — so an implementation may be reused across
  * ticks only by the firing side. Fired on the server thread only; not thread-safe.
  */
trait WetnessContributionCallback {

  /** Queries a listener for one player's wetness contribution this tick. Implementations combine
    * the player's own state with the frame's snapshot, then write any resulting rate into the
    * context: positive rates wet (immersion, rain, sweat), negative rates dry.
    *
    * @param player
    *   the player being ticked: the listener's subject and its window onto game state
    * @param frame
    *   this tick's body-and-environment snapshot; valid only for the duration of this synchronous
    *   dispatch
    * @param context
    *   the write-only accumulator the contribution goes into; do not retain it or call it outside
    *   the callback
    */
  def contribute(
      player: ServerPlayer,
      frame: WetnessContributionCallback.Frame,
      context: WetnessContributionContext
  ): Unit
}

object WetnessContributionCallback {

  /** Per-tick body-and-environment snapshot handed to listeners alongside the context. Server
    * thread only; do not retain it beyond the dispatch.
    *
    * @param coreTemperature
    *   this tick's freshly written core body temperature (°C); sweat gates on it
    * @param apparentTemperature
    *   the biome temperature mapped to °C, floored at 0 while immersed
    * @param airDryness
    *   `1 - biome downfall`, the air's headroom for evaporation: a jungle reads ~0, a desert ~1
    * @param immersed
    *   whether the player is in water this tick
    * @param raining
    *   whether rain reaches the player this tick (always false while immersed)
    */
  final case class Frame(
      coreTemperature: Double,
      apparentTemperature: Double,
      airDryness: Double,
      immersed: Boolean,
      raining: Boolean
  )

  val EVENT: Event[WetnessContributionCallback] = EventFactory.createArrayBacked(
    classOf[WetnessContributionCallback],
    (listeners: Array[WetnessContributionCallback]) =>
      (player: ServerPlayer, frame: Frame, context: WetnessContributionContext) =>
        listeners.foreach(_.contribute(player, frame, context))
  )

  /** Default mutable context: a single signed accumulator. The firing side creates one per dispatch
    * and reads the total afterwards; listeners must not retain it or call it outside the callback.
    * Not thread-safe: use on the server thread only.
    */
  final class Accumulator extends WetnessContributionContext {
    private var total: Double = 0.0

    override def add(ratePerSecond: Double): Unit = {
      total += ratePerSecond
    }

    /** Sum of all [[add]] rates since construction. */
    def totalPerSecond: Double = total
  }
}
