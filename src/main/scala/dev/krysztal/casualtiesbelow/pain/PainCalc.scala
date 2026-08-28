package dev.krysztal.casualtiesbelow.pain

import net.minecraft.world.entity.LivingEntity

import dev.krysztal.casualtiesbelow.api.body.BodyComponent
import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.api.body.LimbCondition
import dev.krysztal.casualtiesbelow.api.body.LimbStats
import dev.krysztal.casualtiesbelow.api.data.GameplayDataLookup
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
  *   - '''grants''' — pain injected into one limb by an injury: impact pain from falls ([[onFall]])
  *     and from wound profiles (`WoundProfile.painPerPoint`, applied by `LimbDamage`), both scaling
  *     with the damage, and discrete condition onsets ([[onConditionOnset]], fixed one-time
  *     amounts). Grants flow through the limb injury event context (`LimbInjuryContext.pain`) so
  *     listeners can adjust them; capping at the limb maximum happens at the application site.
  *   - '''derivations''' — whole-body pain ([[total]]), computed on demand from per-limb pain and
  *     never stored. The limbs are the single source of truth (already synced to clients), so both
  *     sides compute the identical value locally; authoritative gameplay decisions must compute it
  *     server-side, client-side results are presentation-only.
  *
  * Fall and condition grants come from the victim's matching `fall_rules` entry; aggregation
  * strategy remains global config-driven math.
  */
object PainCalc {

  /** Impact pain granted to one limb by a fall, per half-heart of formula damage. */
  def onFall(victim: LivingEntity, damage: Double): Double = {
    val rules = GameplayDataLookup.fallRules(victim)
    damage * rules.fallPainPerPoint
  }

  /** One-time pain granted when a discrete condition onsets on a limb. */
  def onConditionOnset(victim: LivingEntity, condition: LimbCondition): Double = {
    val rules = GameplayDataLookup.fallRules(victim)
    condition match {
      case LimbCondition.Fracture    => rules.fracturePain
      case LimbCondition.Dislocation => rules.dislocationPain
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

}
