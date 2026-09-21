package dev.krysztal.casualtiesbelow.physiology.adrenaline

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.extension.ConfigValueExtensions.*

/** Acute-pain multiplier derived from the authoritative adrenaline reserve.
  *
  * The reserve is not consumed. Damage handling snapshots this multiplier after the current event's
  * stimulus, then reuses it for every wound contribution emitted by that event.
  */
object AdrenalinePain {

  def currentMultiplier(vitals: VitalsComponent): Double =
    multiplier(
      Adrenaline.currentAmount(vitals),
      CasualtiesBelowConfig.AdrenalinePainReductionPerPoint.value,
      CasualtiesBelowConfig.AdrenalineMaxPainReductionFraction.value
    )

  private[casualtiesbelow] def multiplier(
      adrenaline: Double,
      reductionPerPoint: Double,
      maxReductionFraction: Double
  ): Double = {
    val reserve = nonNegative(adrenaline)
    val perPoint = nonNegative(reductionPerPoint)
    val maximumReduction = nonNegative(maxReductionFraction).min(1.0)
    val rawReduction = reserve * perPoint
    val reduction =
      if (rawReduction.isFinite) rawReduction.min(maximumReduction)
      else maximumReduction
    1.0 - reduction
  }

  private[casualtiesbelow] def normalizeMultiplier(value: Double): Double = {
    if (value.isFinite) value.max(0.0).min(1.0)
    else 1.0
  }

  private[casualtiesbelow] def scale(value: Double, multiplier: Double): Double = {
    val normalizedMultiplier = normalizeMultiplier(multiplier)
    if (normalizedMultiplier <= 0.0) 0.0 else value * normalizedMultiplier
  }

  private def nonNegative(value: Double): Double = {
    if (value == Double.PositiveInfinity) Double.MaxValue
    else if (value.isFinite) value.max(0.0)
    else 0.0
  }
}
