package dev.krysztal.casualtiesbelow.blood

import dev.krysztal.casualtiesbelow.api.body.VitalsComponent
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

/** Bounded mutations of the server-authoritative blood volume.
  *
  * Ordinary progression paths share these operations so drains, restoration, and effective-cap
  * reconciliation handle invalid stored values and bounds consistently. Admin/reset/serialization
  * code may still assign authoritative values directly.
  */
object BloodVolume {

  /** Healthy configured blood capacity, normalized to a finite non-negative value. */
  def healthyMaximum: Double = nonNegative(CasualtiesBelowConfig.MaxBloodVolume.get())

  /** Current effective capacity after sepsis, normalized to the healthy configured capacity. */
  def effectiveMaximum(vitals: VitalsComponent): Double = {
    normalizeBound(
      CasualtiesBelowConfig.effectiveMaxBloodVolume(vitals.infection.sepsis),
      healthyMaximum
    )
  }

  /** Blood-oxygen carrying capacity represented by the current bounded blood volume. */
  def oxygenCarryingCapacity(vitals: VitalsComponent): Double = {
    oxygenCarryingCapacity(
      vitals.circulation.bloodVolume,
      healthyMaximum,
      CasualtiesBelowConfig.FullOxygenBloodFraction.get()
    )
  }

  /** Pure form of the carrying-capacity relationship, exposed to package tests. Blood at or above
    * `fullOxygenFraction` of healthy volume retains full oxygen capacity; below it, capacity falls
    * linearly to zero. Zero blood always has zero capacity.
    */
  private[blood] def oxygenCarryingCapacity(
      bloodVolume: Double,
      healthyMaximum: Double,
      fullOxygenFraction: Double
  ): Double = {
    val healthy = nonNegative(healthyMaximum)
    val volume = normalizedVolume(bloodVolume, healthy)
    if (healthy <= 0.0 || volume <= 0.0) return 0.0

    val fraction = normalizedFraction(fullOxygenFraction)
    if (fraction <= 0.0) return VitalsComponent.MaxBloodOxygen

    val fullCapacityVolume = healthy * fraction
    (VitalsComponent.MaxBloodOxygen * volume / fullCapacityVolume)
      .min(VitalsComponent.MaxBloodOxygen)
  }

  /** Reconciles blood into `[0, maximum]`; returns whether the stored value changed. */
  def clamp(vitals: VitalsComponentImpl, maximum: Double): Boolean = {
    val boundedMaximum = nonNegative(maximum)
    val next = normalizedVolume(vitals.circulation.bloodVolume, boundedMaximum)
    if (same(vitals.circulation.bloodVolume, next)) return false

    VitalsMutations.setBloodVolume(vitals, next)
    true
  }

  /** Restores a finite non-negative amount without exceeding `maximum`; returns the actual gain. */
  def restore(vitals: VitalsComponentImpl, amount: Double, maximum: Double): Double = {
    val boundedMaximum = nonNegative(maximum)
    val current = normalizedVolume(vitals.circulation.bloodVolume, boundedMaximum)
    val next = (current + mutationAmount(amount)).min(boundedMaximum)
    VitalsMutations.setBloodVolume(vitals, next)
    next - current
  }

  /** Drains a finite non-negative amount without crossing `floor` or `maximum`; returns the actual
    * loss. The floor is normalized into `[0, maximum]` and never raises blood already below it.
    */
  def drain(
      vitals: VitalsComponentImpl,
      amount: Double,
      maximum: Double,
      floor: Double = 0.0
  ): Double = {
    val boundedMaximum = nonNegative(maximum)
    val boundedFloor = normalizeBound(floor, boundedMaximum)
    val current = normalizedVolume(vitals.circulation.bloodVolume, boundedMaximum)
    val effectiveFloor = boundedFloor.min(current)
    val next = (current - mutationAmount(amount)).max(effectiveFloor)
    VitalsMutations.setBloodVolume(vitals, next)
    current - next
  }

  /** Normalizes a blood value for read-only comparisons without mutating the component. */
  def normalizedVolume(value: Double, maximum: Double): Double = {
    val boundedMaximum = nonNegative(maximum)
    if (value == Double.PositiveInfinity) boundedMaximum
    else if (value.isFinite) value.max(0.0).min(boundedMaximum)
    else 0.0
  }

  private def normalizeBound(value: Double, maximum: Double): Double = {
    val boundedMaximum = nonNegative(maximum)
    if (value == Double.PositiveInfinity) boundedMaximum
    else if (value.isFinite) value.max(0.0).min(boundedMaximum)
    else 0.0
  }

  private def nonNegative(value: Double): Double = {
    if (value == Double.PositiveInfinity) Double.MaxValue
    else if (value.isFinite) value.max(0.0)
    else 0.0
  }

  private def normalizedFraction(value: Double): Double = {
    if (value == Double.PositiveInfinity) 1.0
    else if (value.isFinite) value.max(0.0).min(1.0)
    else 0.0
  }

  private def mutationAmount(value: Double): Double = {
    if (value == Double.PositiveInfinity) Double.MaxValue
    else if (value.isFinite) value.max(0.0)
    else 0.0
  }

  private def same(left: Double, right: Double): Boolean = {
    java.lang.Double.doubleToLongBits(left) == java.lang.Double.doubleToLongBits(right)
  }
}
