package dev.krysztal.casualtiesbelow.pain

import dev.krysztal.casualtiesbelow.component.BodyComponent
import dev.krysztal.casualtiesbelow.component.BodyPart
import dev.krysztal.casualtiesbelow.component.LimbCondition
import dev.krysztal.casualtiesbelow.component.LimbStats
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

/** How per-limb pains are aggregated into whole-body pain. Explicitly extends [[java.lang.Enum]]
  * (Scala 3 enums otherwise only extend `scala.reflect.Enum`) so it works with Java's F-bounded
  * enum APIs — e.g. NeoForge's `ModConfigSpec.Builder.defineEnum`.
  */
enum TotalPainStrategy extends Enum[TotalPainStrategy] {

  /** The worst single pain; additional injuries don't add up. */
  case Max

  /** Plain sum of all limb pains (clamped to [[LimbStats.MaxValue]] like every strategy). */
  case Sum

  /** Geometric-decay sum over descending-sorted pains; see [[PainCalc.total]]. Honors the
    * configured decay factor and filter threshold.
    */
  case Geometric
}

/** All pain math used by the game, in one place.
  *
  * Two kinds of pain exist:
  *
  *   - '''grants''' — pain injected into one limb by an injury: impact pain ([[onFall]],
  *     [[onMelee]]; scales with the damage) and discrete condition onsets ([[onConditionOnset]],
  *     fixed one-time amounts). Grants flow through the limb injury event context
  *     (`LimbInjuryContext.pain`) so listeners can adjust them; capping at the limb maximum happens
  *     at the application site.
  *   - '''derivations''' — whole-body pain ([[total]]), computed on demand from per-limb pain and
  *     never stored. The limbs are the single source of truth (already synced to clients), so both
  *     sides compute the identical value locally; authoritative gameplay decisions must compute it
  *     server-side, client-side results are presentation-only.
  *
  * The numeric constants here are balancing placeholders; expect them to become config values once
  * playtesting starts.
  */
object PainCalc {

  /** Impact pain granted to one limb by a fall, per half-heart of formula damage. */
  def onFall(damage: Double): Double = damage * FallPainPerPoint

  /** Impact pain granted to one limb by a melee hit, per half-heart of taken damage. */
  def onMelee(damage: Double): Double = damage * MeleePainPerPoint

  /** One-time pain granted when a discrete condition onsets on a limb. */
  def onConditionOnset(condition: LimbCondition): Double =
    condition match {
      case LimbCondition.Fracture    => FracturePain
      case LimbCondition.Dislocation => DislocationPain
    }

  /** Whole-body pain for the given body, from all limbs' current pain. */
  def total(body: BodyComponent): Double =
    total(BodyPart.values.map(part => body.stats(part).pain))

  /** Whole-body pain from a collection of limb pain values, using the configured strategy. */
  def total(pains: Iterable[Double]): Double = {
    val values = pains.toSeq
    val result = CasualtiesBelowConfig.PainStrategy.get() match {
      case TotalPainStrategy.Max       => values.maxOption.getOrElse(0.0)
      case TotalPainStrategy.Sum       => values.sum
      case TotalPainStrategy.Geometric => geometric(values)
    }
    math.min(result, LimbStats.MaxValue)
  }

  /** `min(100, Σ pᵢ·dⁱ)` over pains ≥ the filter threshold, sorted descending; if all are filtered
    * out, falls back to `max`.
    */
  private def geometric(pains: Seq[Double]): Double = {
    val decay = CasualtiesBelowConfig.TotalPainDecay.get()
    val filterThreshold = CasualtiesBelowConfig.TotalPainFilterThreshold.get()

    val sorted = pains.filter(_ >= filterThreshold).sortBy(-_)
    // When every pain is below the filter threshold, the worst single pain still counts.
    val considered =
      if (sorted.nonEmpty) sorted
      else pains.maxOption.toSeq

    considered.zipWithIndex.map { (pain, i) =>
      pain * math.pow(decay, i)
    }.sum
  }

  /** Impact pain per half-heart of fall damage. */
  private val FallPainPerPoint = 6.0

  /** Impact pain per half-heart of melee damage. */
  private val MeleePainPerPoint = 4.0

  /** One-time pain granted when a limb fractures. */
  private val FracturePain = 50.0

  /** One-time pain granted when a limb is dislocated. */
  private val DislocationPain = 30.0
}
