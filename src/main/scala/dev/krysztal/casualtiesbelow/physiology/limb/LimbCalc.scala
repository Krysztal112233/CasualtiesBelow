package dev.krysztal.casualtiesbelow.physiology.limb

import dev.krysztal.casualtiesbelow.physiology.dirtiness.Dirtiness

/** Pure formula steps extracted from [[Limb]] so every stage can be unit-tested without a running
  * game: the evolution pass reads config and world state, then delegates the math here.
  */
object LimbCalc {

  /** Wound infection onset chance per tick: the base rate scaled by the risk setting, the wound's
    * skin-damage fraction, and the dirtiness multiplier. Independent of existing-infection
    * progression and contagious spread.
    */
  def woundOnsetChance(
      skinDamage: Double,
      dirtiness: Double,
      riskMultiplier: Double,
      baseChancePerTick: Double,
      maxIntegrity: Double,
      dirtinessMaxValue: Double,
      dirtinessMultiplierAtMax: Double
  ): Double = {
    baseChancePerTick * riskMultiplier *
      skinDamage / maxIntegrity *
      Dirtiness.infectionChanceMultiplier(dirtiness, dirtinessMaxValue, dirtinessMultiplierAtMax)
  }

  /** One tick of infection progress: the spread rate grows with the immune complement while the
    * immune system fights with its `fightShare` of capacity. May return zero or negative, which the
    * caller treats as cleared.
    */
  def nextInfectionProgress(
      progress: Double,
      spreadPerTick: Double,
      fightPerTick: Double,
      immuneFraction: Double,
      fightShare: Double,
      maxValue: Double
  ): Double = {
    (progress + spreadPerTick * (1.0 - immuneFraction) -
      fightPerTick * immuneFraction * fightShare)
      .min(maxValue)
  }

  /** Pain after an infection grant under systemic hyperalgesia: only positive grants and
    * multipliers count, clamped to the limb's maximum.
    */
  def infectionPainAfterGrant(
      pain: Double,
      grant: Double,
      painGrantMultiplier: Double,
      maxValue: Double
  ): Double = {
    (pain + grant.max(0.0) * painGrantMultiplier.max(0.0)).min(maxValue)
  }

  /** Immune-scaled multiplier interpolating between `minMultiplier` (zero immune) and 1.0 (full
    * immune).
    */
  def immuneScaledMultiplier(minMultiplier: Double, immuneFraction: Double): Double = {
    minMultiplier + (1.0 - minMultiplier) * immuneFraction
  }

  /** Tissue damage fraction of a limb: the average of missing muscle and skin integrity. */
  def tissueDamageFraction(muscleHealth: Double, skinIntegrity: Double, maxValue: Double): Double =
    (2.0 - muscleHealth / maxValue - skinIntegrity / maxValue) / 2.0

  /** Walking-strain pain rate for a leg: zero unless it bears the walking player; fracture and
    * dislocation are independent conditions whose rates stack.
    */
  def walkingStrainRate(
      bearingWeight: Boolean,
      fractured: Boolean,
      dislocated: Boolean,
      fractureRatePerTick: Double,
      dislocationRatePerTick: Double
  ): Double = {
    if (!bearingWeight) 0.0
    else {
      (if (fractured) fractureRatePerTick else 0.0) +
        (if (dislocated) dislocationRatePerTick else 0.0)
    }
  }
}
