package dev.krysztal.casualtiesbelow.physiology.adrenaline

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extension.Prelude.*

/** Acute-pain multiplier derived from the authoritative adrenaline reserve.
  *
  * The reserve is not consumed. Damage handling snapshots this multiplier after the current event's
  * stimulus, then reuses it for every wound contribution emitted by that event.
  */
object AdrenalinePain {

  def currentMultiplier(vitals: VitalsComponent): Double =
    multiplier(
      Adrenaline.currentAmount(vitals),
      Consts.Adrenaline.PainReductionPerPoint,
      Consts.Adrenaline.MaxPainReductionFraction
    )

  private[casualtiesbelow] def multiplier(
      adrenaline: Double,
      reductionPerPoint: Double,
      maxReductionFraction: Double
  ): Double = {
    val reserve = adrenaline.nonNegative
    val perPoint = reductionPerPoint.nonNegative
    val maximumReduction = maxReductionFraction.nonNegative.min(1.0)
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

}
