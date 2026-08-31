package dev.krysztal.casualtiesbelow.pain

import dev.krysztal.casualtiesbelow.api.body.limb.BodyComponent
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.api.body.limb.LimbCondition
import dev.krysztal.casualtiesbelow.api.body.limb.LimbSnapshot
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

/** How per-limb pains are aggregated into whole-body pain. Explicitly extends [[java.lang.Enum]]
  * (Scala 3 enums otherwise only extend `scala.reflect.Enum`) so it works with Java's F-bounded
  * enum APIs — e.g. NeoForge's `ModConfigSpec.Builder.defineEnum`.
  */
enum TotalPainStrategy extends Enum[TotalPainStrategy] {

  /** The worst single pain; additional injuries don't add up. */
  case Max

  /** Plain sum of all limb pains (clamped to [[LimbSnapshot.MaxValue]] like every strategy). */
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
  *   - '''grants''' — pain injected into one limb by an injury: impact pain from wound profiles
  *     (`WoundProfile.painPerPoint`, applied by `WoundApplications`) scales with damage, while
  *     discrete condition onsets ([[onConditionOnset]]) carry fixed one-time amounts. Grants flow
  *     through the limb injury event context (`LimbInjuryContext.basePain`) after internal
  *     acute-pain mitigation, so listeners can adjust them; capping at the limb maximum happens at
  *     the application site.
  *   - '''derivations''' — whole-body pain ([[total]]), computed on demand from per-limb pain and
  *     never stored. The limbs are the single source of truth (already synced to clients), so both
  *     sides compute the identical value locally; authoritative gameplay decisions must compute it
  *     server-side, client-side results are presentation-only.
  *
  * Wound grants come from the classified profile; callers supply fixed condition grants from the
  * applicable gameplay rules. Aggregation strategy remains global config-driven math.
  */
object PainCalc {

  /** One-time pain granted when a discrete condition onsets on a limb. */
  def onConditionOnset(
      condition: LimbCondition,
      fracturePain: Double,
      dislocationPain: Double
  ): Double = {
    condition match {
      case LimbCondition.Fracture    => fracturePain
      case LimbCondition.Dislocation => dislocationPain
    }
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
    math.min(result, LimbSnapshot.MaxValue)
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

}
