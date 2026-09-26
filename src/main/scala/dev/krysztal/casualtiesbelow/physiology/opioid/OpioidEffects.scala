package dev.krysztal.casualtiesbelow.physiology.opioid

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*

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
    Consts.Opioid.OpioidSedationCeilingFormula.evaluate(level).bounded(VitalsComponent.MaxValue)
  }

  def respiratoryEfficiency(level: Double, dependence: Double): Double = {
    Consts.Opioid.OpioidRespiratoryEfficiencyFormula.evaluate(level, dependence).boundedFraction
  }

  def causesRespiratoryFailure(
      efficiency: Double,
      threshold: Double = Consts.Opioid.RespiratoryFailureEfficiencyThreshold
  ): Boolean = {
    efficiency < threshold
  }

}
