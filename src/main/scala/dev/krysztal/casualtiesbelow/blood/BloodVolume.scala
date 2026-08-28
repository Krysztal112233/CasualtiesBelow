package dev.krysztal.casualtiesbelow.blood

import dev.krysztal.casualtiesbelow.api.body.VitalsComponent
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
      CasualtiesBelowConfig.effectiveMaxBloodVolume(vitals.sepsis),
      healthyMaximum
    )
  }

  /** Blood-oxygen carrying capacity represented by the current bounded blood volume. */
  def oxygenCarryingCapacity(vitals: VitalsComponent): Double = {
    val healthy = healthyMaximum
    if (healthy <= 0.0) return 0.0

    VitalsComponent.MaxBloodOxygen * normalizedVolume(vitals.bloodVolume, healthy) / healthy
  }

  /** Reconciles blood into `[0, maximum]`; returns whether the stored value changed. */
  def clamp(vitals: VitalsComponent, maximum: Double): Boolean = {
    val boundedMaximum = nonNegative(maximum)
    val next = normalizedVolume(vitals.bloodVolume, boundedMaximum)
    if (same(vitals.bloodVolume, next)) return false

    vitals.bloodVolume = next
    true
  }

  /** Restores a finite non-negative amount without exceeding `maximum`; returns the actual gain. */
  def restore(vitals: VitalsComponent, amount: Double, maximum: Double): Double = {
    val boundedMaximum = nonNegative(maximum)
    val current = normalizedVolume(vitals.bloodVolume, boundedMaximum)
    val next = (current + mutationAmount(amount)).min(boundedMaximum)
    vitals.bloodVolume = next
    next - current
  }

  /** Drains a finite non-negative amount without crossing `floor` or `maximum`; returns the actual
    * loss. The floor is normalized into `[0, maximum]` and never raises blood already below it.
    */
  def drain(
      vitals: VitalsComponent,
      amount: Double,
      maximum: Double,
      floor: Double = 0.0
  ): Double = {
    val boundedMaximum = nonNegative(maximum)
    val boundedFloor = normalizeBound(floor, boundedMaximum)
    val current = normalizedVolume(vitals.bloodVolume, boundedMaximum)
    val effectiveFloor = boundedFloor.min(current)
    val next = (current - mutationAmount(amount)).max(effectiveFloor)
    vitals.bloodVolume = next
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

  private def mutationAmount(value: Double): Double = {
    if (value == Double.PositiveInfinity) Double.MaxValue
    else if (value.isFinite) value.max(0.0)
    else 0.0
  }

  private def same(left: Double, right: Double): Boolean = {
    java.lang.Double.doubleToLongBits(left) == java.lang.Double.doubleToLongBits(right)
  }
}
