package dev.krysztal.casualtiesbelow.physiology.opioid

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

/** Pure opioid effect curves. Stored vitals remain unmodified; consumers opt into the relevant
  * derived pressure explicitly.
  */
object OpioidEffects {

  def feltPain(rawPain: Double, vitals: VitalsComponent): Double = {
    feltPain(rawPain, vitals.opioidLevel, vitals.opioidDependence)
  }

  private[opioid] def feltPain(rawPain: Double, level: Double, dependence: Double): Double = {
    rawPain * (1.0 - analgesiaFraction(level, dependence))
  }

  def analgesiaFraction(level: Double, dependence: Double): Double = {
    boundedFraction(CasualtiesBelowConfig.opioid.opioidAnalgesiaFormula.evaluate(level, dependence))
  }

  def consciousnessCeiling(level: Double): Double = {
    bounded(
      CasualtiesBelowConfig.opioid.opioidSedationCeilingFormula.evaluate(level),
      VitalsComponent.MaxValue
    )
  }

  def respiratoryEfficiency(level: Double, dependence: Double): Double = {
    boundedFraction(
      CasualtiesBelowConfig.opioid.opioidRespiratoryEfficiencyFormula.evaluate(level, dependence)
    )
  }

  def causesRespiratoryFailure(efficiency: Double): Boolean = {
    causesRespiratoryFailure(
      efficiency,
      CasualtiesBelowConfig.opioid.respiratoryFailureEfficiencyThreshold.get()
    )
  }

  private[opioid] def causesRespiratoryFailure(efficiency: Double, threshold: Double): Boolean = {
    efficiency < threshold
  }

  private def boundedFraction(value: Double): Double = bounded(value, 1.0)

  private def bounded(value: Double, maximum: Double): Double = {
    if (value == Double.PositiveInfinity) maximum
    else if (value.isFinite) value.max(0.0).min(maximum)
    else 0.0
  }
}
