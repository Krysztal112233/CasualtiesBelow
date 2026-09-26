package dev.krysztal.casualtiesbelow.physiology.circulation

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*

/** Bounded mutations of the server-authoritative blood volume.
  *
  * Ordinary progression paths share these operations so drains, restoration, and effective-cap
  * reconciliation handle invalid stored values and bounds consistently. Admin/reset/serialization
  * code may still assign authoritative values directly.
  */
private[casualtiesbelow] object BloodVolume {

  /** Healthy configured blood capacity, normalized to a finite non-negative value. */
  def healthyMaximum: Double = Consts.Vitals.MaxBloodVolume.nonNegative

  /** Current effective capacity after sepsis, normalized to the healthy configured capacity. */
  def effectiveMaximum(vitals: VitalsComponent): Double = {
    Consts.Vitals.effectiveMaxBloodVolume(vitals.infection.sepsis).bounded(healthyMaximum)
  }

  /** Blood-oxygen carrying capacity represented by the current bounded blood volume. */
  def oxygenCarryingCapacity(vitals: VitalsComponent): Double = {
    oxygenCarryingCapacity(vitals.circulation.bloodVolume)
  }

  /** Pure form of the carrying-capacity relationship, exposed to package tests. Blood at or above
    * `fullOxygenFraction` of healthy volume retains full oxygen capacity; below it, capacity falls
    * linearly to zero. Zero blood always has zero capacity.
    */
  private[circulation] def oxygenCarryingCapacity(
      bloodVolume: Double,
      healthyMaximum: Double = BloodVolume.healthyMaximum,
      fullOxygenFraction: Double = Consts.Vitals.FullOxygenBloodFraction
  ): Double = {
    val healthy = healthyMaximum.nonNegative
    val volume = bloodVolume.bounded(healthy)
    if (healthy <= 0.0 || volume <= 0.0) return 0.0

    val fraction = fullOxygenFraction.boundedFraction
    if (fraction <= 0.0) return VitalsComponent.MaxBloodOxygen

    val fullCapacityVolume = healthy * fraction
    (VitalsComponent.MaxBloodOxygen * volume / fullCapacityVolume)
      .min(VitalsComponent.MaxBloodOxygen)
  }

  /** Reconciles blood into `[0, maximum]`; returns whether the stored value changed. */
  def clamp(vitals: VitalsComponentImpl, maximum: Double): Boolean = {
    val boundedMaximum = maximum.nonNegative
    val next = vitals.circulation.bloodVolume.bounded(boundedMaximum)
    if (vitals.circulation.bloodVolume.sameBits(next)) return false

    vitals.setBloodVolume(next)
    true
  }

  /** Restores a finite non-negative amount without exceeding `maximum`; returns the actual gain. */
  def restore(vitals: VitalsComponentImpl, amount: Double, maximum: Double): Double = {
    val boundedMaximum = maximum.nonNegative
    val current = vitals.circulation.bloodVolume.bounded(boundedMaximum)
    val next = (current + amount.nonNegative).min(boundedMaximum)
    vitals.setBloodVolume(next)
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
    val boundedMaximum = maximum.nonNegative
    val boundedFloor = floor.bounded(boundedMaximum)
    val current = vitals.circulation.bloodVolume.bounded(boundedMaximum)
    val effectiveFloor = boundedFloor.min(current)
    val next = (current - amount.nonNegative).max(effectiveFloor)
    vitals.setBloodVolume(next)
    current - next
  }

  /** Normalizes a blood value for read-only comparisons without mutating the component. */
  def normalizedVolume(value: Double, maximum: Double): Double = {
    value.bounded(maximum)
  }
}
