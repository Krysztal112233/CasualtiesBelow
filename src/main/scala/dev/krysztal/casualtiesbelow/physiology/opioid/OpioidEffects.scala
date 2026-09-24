package dev.krysztal.casualtiesbelow.physiology.opioid

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.Consts

/** Pure opioid effect curves. Stored vitals remain unmodified; consumers opt into the relevant
  * derived pressure explicitly.
  */
object OpioidEffects {

  /** Effective acute opioid exposure after tolerance: the raw level damped linearly by dependence
    * (0 dependence = full level, 100 dependence = half level).
    */
  def effectiveLevel(level: Double, dependence: Double): Double = {
    level.max(0.0) / (1.0 + dependence.max(0.0) / 100.0)
  }

  /** Limb pain drained per tick by effective opioid exposure, on top of natural pain decay. The
    * drain consumes stored pain, so the relief outlasts the drug itself.
    */
  def painDrainPerTick(level: Double, dependence: Double): Double = {
    painDrainPerTick(
      level,
      dependence,
      Consts.Opioid.OpioidPainDrainPerLevelPerTick *
        CasualtiesBelowConfig.medicineFood.opioidPainReliefMultiplier.get()
    )
  }

  private[opioid] def painDrainPerTick(
      level: Double,
      dependence: Double,
      drainPerLevelPerTick: Double
  ): Double = {
    effectiveLevel(level, dependence) * drainPerLevelPerTick
  }

  def consciousnessCeiling(level: Double): Double = {
    bounded(
      Consts.Opioid.OpioidSedationCeilingFormula.evaluate(level),
      VitalsComponent.MaxValue
    )
  }

  def respiratoryEfficiency(level: Double, dependence: Double): Double = {
    boundedFraction(
      Consts.Opioid.OpioidRespiratoryEfficiencyFormula.evaluate(level, dependence)
    )
  }

  def causesRespiratoryFailure(
      efficiency: Double,
      threshold: Double = Consts.Opioid.RespiratoryFailureEfficiencyThreshold
  ): Boolean = {
    efficiency < threshold
  }

  private def boundedFraction(value: Double): Double = bounded(value, 1.0)

  private def bounded(value: Double, maximum: Double): Double = {
    if (value == Double.PositiveInfinity) maximum
    else if (value.isFinite) value.max(0.0).min(maximum)
    else 0.0
  }
}
