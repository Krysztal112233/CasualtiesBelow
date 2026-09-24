package dev.krysztal.casualtiesbelow.physiology.limb

import net.minecraft.util.RandomSource

import dev.krysztal.casualtiesbelow.component.MutableLimbState
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.Consts

/** External-wound and bleeding calculations shared by damage attribution and injury progression.
  *
  * A wound-capable injury first reduces skin integrity, then grants bleeding capped from the
  * resulting skin state. The cap is linear in skin damage: intact skin cannot bleed and fully
  * destroyed skin permits [[CasualtiesBelowConfig.injurySurvival.maxExternalBleedingRate]].
  * Repeated wounds can still increase bleeding after skin reaches zero, up to that cap.
  */
object BleedingCalc {

  /** Applies one external wound: reduce skin integrity, then add the source's bleeding rate capped
    * by the updated skin state. The granted rate is rolled as `rate × (1 ± worldPulseJitter)` —
    * proportional fluctuation, so larger wounds fluctuate more in absolute terms. Non-positive skin
    * damage is not a wound and does nothing.
    */
  def applyWound(
      stats: MutableLimbState,
      skinDamage: Double,
      bleedingRate: Double,
      random: RandomSource
  ): Unit = {
    if (skinDamage <= 0.0) return

    val jitter = Consts.Randomness.WorldPulseJitter
    val rolledRate =
      bleedingRate * (1.0 + (random.nextDouble() * 2.0 - 1.0) * jitter)
    stats.skinIntegrity = (stats.skinIntegrity - skinDamage).max(0.0)
    stats.externalBleedingRate =
      (stats.externalBleedingRate + rolledRate.max(0.0)).min(cap(stats.skinIntegrity))
  }

  /** Removes a fraction of the limb's current external bleeding rate. The defensive clamp keeps
    * internal callers safe even though datapack codecs already constrain the fraction to `(0, 1]`.
    */
  def applyHemostasis(stats: MutableLimbState, reductionFraction: Double): Unit = {
    val retainedFraction = 1.0 - reductionFraction.max(0.0).min(1.0)
    stats.externalBleedingRate = (stats.externalBleedingRate * retainedFraction).max(0.0)
  }

  /** Subtracts a fixed rate from current external bleeding. Non-positive reductions do nothing, and
    * a reduction larger than the current rate stops the bleeding without going negative.
    */
  def applyFixedHemostasis(stats: MutableLimbState, reductionRate: Double): Unit = {
    stats.externalBleedingRate = (stats.externalBleedingRate - reductionRate.max(0.0)).max(0.0)
  }

  /** Maximum external bleeding rate for the given skin integrity: linear from zero on intact skin
    * to the configured maximum on fully destroyed skin. Clamping makes this safe for malformed or
    * legacy component values outside the normal 0–100 range.
    */
  def cap(skinIntegrity: Double): Double = {
    val skinDamageFraction =
      (1.0 - skinIntegrity / MutableLimbState.MaxValue).max(0.0).min(1.0)
    CasualtiesBelowConfig.injurySurvival.maxExternalBleedingRate.get() * skinDamageFraction
  }
}
