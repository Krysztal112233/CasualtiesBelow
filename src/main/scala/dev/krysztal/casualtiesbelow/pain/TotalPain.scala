package dev.krysztal.casualtiesbelow.pain

import java.lang.Enum

import dev.krysztal.casualtiesbelow.component.BodyComponent
import dev.krysztal.casualtiesbelow.component.BodyPart
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

  /** Geometric-decay sum over descending-sorted pains; see [[TotalPain]]. Honors the configured
    * decay factor and filter threshold.
    */
  case Geometric
}

/** Whole-body pain, derived on demand from per-limb pain — never stored. The limbs are the single
  * source of truth (already synced to clients), so a persisted total would only risk drifting out
  * of sync, and both sides can compute the identical value locally.
  *
  * The aggregation strategy is configurable ([[CasualtiesBelowConfig.TotalPainStrategy]]). The
  * default, [[TotalPainStrategy.Geometric]], is a geometric-decay sum: limb pains are sorted
  * descending and accumulated with weights `d⁰, d¹, d², …`, so the worst injury dominates and each
  * additional one contributes less — many small wounds cannot stack into agony the way a plain sum
  * would allow. Pains below the configured filter threshold are excluded entirely (scrapes don't
  * add up); if every pain is filtered out, the total falls back to the worst single pain. Every
  * strategy's result is clamped to [[LimbStats.MaxValue]].
  *
  * Pure function of its inputs: safe to call on either side. Authoritative gameplay decisions must
  * compute it server-side; client-side results are presentation-only.
  */
object TotalPain {

  /** Whole-body pain for the given body, from all limbs' current pain. */
  def of(body: BodyComponent): Double =
    of(BodyPart.values.map(part => body.stats(part).pain))

  /** Whole-body pain from a collection of limb pain values, using the configured strategy. */
  def of(pains: Iterable[Double]): Double = {
    val values = pains.toSeq
    val total = CasualtiesBelowConfig.PainStrategy.get() match {
      case TotalPainStrategy.Max       => values.maxOption.getOrElse(0.0)
      case TotalPainStrategy.Sum       => values.sum
      case TotalPainStrategy.Geometric => geometric(values)
    }
    math.min(total, LimbStats.MaxValue)
  }

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
