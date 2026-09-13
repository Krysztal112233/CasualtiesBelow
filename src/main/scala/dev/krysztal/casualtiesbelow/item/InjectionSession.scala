package dev.krysztal.casualtiesbelow.item

/** Client-side accumulator for one injection screen session. Tracks how many droplets have been
  * pushed (including fractional progress between reports) and the speed-weighted integral needed
  * for side-effect settlement, emitting whole-droplet [[InjectionBatch]]es on flush.
  *
  * Whole droplets are the wire unit so client and server stay exactly in sync: the sum of all
  * flushed batches can never exceed the syringe's initial contents, and the final flush reports the
  * exact remaining droplets so the syringe empties deterministically.
  *
  * Pure state machine with no Minecraft dependencies so unit tests can drive it directly.
  *
  * @param fullDroplets
  *   capacity of a completely filled syringe (side-effect tuning anchor)
  * @param initialDroplets
  *   droplets present when the session starts (less than full when resuming an aborted injection)
  */
private[casualtiesbelow] final class InjectionSession(
    val fullDroplets: Long,
    initialDroplets: Long
) {
  require(fullDroplets > 0L, "fullDroplets must be positive")

  private val initial: Long = initialDroplets.max(0L).min(fullDroplets)
  private var sent: Long = 0L
  private var pendingDroplets: Double = 0.0
  private var pendingSpeedSum: Double = 0.0

  /** One flush report: whole droplets pushed and their droplet-weighted average speed fraction. */
  final case class InjectionBatch(droplets: Long, averageSpeed: Double)

  /** Droplets pushed this session, including unflushed fractional progress. */
  private def injectedExact: Double = sent + pendingDroplets

  /** Droplets not yet pushed this session, including fractional progress. */
  def remainingExact: Double = initial - injectedExact

  /** True once the plunger has reached the bottom (within floating point slack). */
  def fullyInjected: Boolean = remainingExact <= 1.0e-9

  /** Fraction of a full syringe currently remaining, for the barrel's liquid rendering. */
  def remainingFractionOfFull: Double = remainingExact / fullDroplets.toDouble

  /** Droplets the syringe stack should hold once every batch flushed so far has settled: the
    * session's initial contents minus all reported droplets. Unflushed fractional progress does not
    * move this baseline. Sent with each batch so the server can verify it settles against the same
    * syringe the screen was opened for.
    */
  def expectedStackDroplets: Long = initial - sent

  /** Advances the plunger by `droplets` pushed at `speedFraction` of maximum speed. Droplets beyond
    * the remaining amount are clamped away so a session can never over-report.
    */
  def advance(speedFraction: Double, droplets: Double): Unit = {
    if (droplets <= 0.0 || droplets.isNaN || fullyInjected) return
    val applied = droplets.min(remainingExact)
    pendingDroplets += applied
    pendingSpeedSum += clampUnit(speedFraction) * applied
  }

  /** Emits the next batch, if any. Mid-session flushes report only whole droplets (the fractional
    * residue carries into the next batch); once the plunger bottoms out the flush reports the exact
    * remaining droplets so the syringe empties exactly. Subsequent flushes return None.
    */
  def flush(): Option[InjectionBatch] = {
    if (fullyInjected && sent < initial) {
      val whole = initial - sent
      val batch = InjectionBatch(whole, averagePendingSpeed)
      sent = initial
      pendingDroplets = 0.0
      pendingSpeedSum = 0.0
      Some(batch)
    } else {
      val whole = math.floor(pendingDroplets).toLong
      if (whole <= 0L) None
      else {
        val average = averagePendingSpeed
        sent += whole
        pendingDroplets -= whole.toDouble
        pendingSpeedSum -= average * whole.toDouble
        Some(InjectionBatch(whole, average))
      }
    }
  }

  private def averagePendingSpeed: Double = {
    if (pendingDroplets <= 0.0) 0.0 else clampUnit(pendingSpeedSum / pendingDroplets)
  }

  private def clampUnit(value: Double): Double = {
    if (value.isNaN || value <= 0.0) 0.0 else if (value >= 1.0) 1.0 else value
  }
}
