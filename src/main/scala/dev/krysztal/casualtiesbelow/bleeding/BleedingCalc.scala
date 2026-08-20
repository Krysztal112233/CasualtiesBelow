package dev.krysztal.casualtiesbelow.bleeding

import net.minecraft.util.RandomSource

import dev.krysztal.casualtiesbelow.component.LimbStats
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

/** External-wound and bleeding calculations shared by damage attribution and injury progression.
  *
  * A wound-capable injury first reduces skin integrity, then grants bleeding capped from the
  * resulting skin state. The cap is linear in skin damage: intact skin cannot bleed and fully
  * destroyed skin permits [[CasualtiesBelowConfig.MaxExternalBleedingRate]]. Repeated wounds can
  * still increase bleeding after skin reaches zero, up to that cap.
  */
object BleedingCalc {

  /** Applies one external wound: reduce skin integrity, then add the source's bleeding rate capped
    * by the updated skin state. The granted rate is rolled as `rate × (1 ± bleedingRateJitter)` —
    * proportional fluctuation, so larger wounds fluctuate more in absolute terms. Non-positive skin
    * damage is not a wound and does nothing.
    */
  def applyWound(
      stats: LimbStats,
      skinDamage: Double,
      bleedingRate: Double,
      random: RandomSource
  ): Unit = {
    if (skinDamage <= 0.0) return

    val jitter = CasualtiesBelowConfig.BleedingRateJitter.get()
    val rolledRate =
      bleedingRate * (1.0 + (random.nextDouble() * 2.0 - 1.0) * jitter)
    stats.skinIntegrity = (stats.skinIntegrity - skinDamage).max(0.0)
    stats.externalBleedingRate =
      (stats.externalBleedingRate + rolledRate.max(0.0)).min(cap(stats.skinIntegrity))
  }

  /** Maximum external bleeding rate for the given skin integrity: linear from zero on intact skin
    * to the configured maximum on fully destroyed skin. Clamping makes this safe for malformed or
    * legacy component values outside the normal 0–100 range.
    */
  def cap(skinIntegrity: Double): Double = {
    val skinDamageFraction =
      (1.0 - skinIntegrity / LimbStats.MaxValue).max(0.0).min(1.0)
    CasualtiesBelowConfig.MaxExternalBleedingRate.get() * skinDamageFraction
  }
}
