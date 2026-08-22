package dev.krysztal.casualtiesbelow.api.body

/** Per-limb stats.
  *
  * `muscleHealth`, `skinIntegrity` and `pain` range from 0 to [[LimbStats.MaxValue]]. All numeric
  * stats are `Double`: they evolve continuously (per-tick accumulation), where `Float`'s 24-bit
  * mantissa would eventually swallow small deltas.
  *
  * `fractureRecoveryTicks` is `None` when the limb is not fractured; a value is the number of ticks
  * remaining until the fracture heals. `infectionProgress` is `None` when the limb is not infected;
  * a value is the infection progress from 0 to [[LimbStats.MaxValue]]. `externalBleedingRate` is
  * the amount of blood lost per tick in mL, capped proportionally to the skin damage (see
  * `InjuryProgression`).
  */
final case class LimbStats(
    var muscleHealth: Double = LimbStats.MaxValue,
    var skinIntegrity: Double = LimbStats.MaxValue,
    var fractureRecoveryTicks: Option[Int] = None,
    var infectionProgress: Option[Double] = None,
    var dislocated: Boolean = false,
    var externalBleedingRate: Double = 0.0,
    var pain: Double = 0.0
)

object LimbStats {
  val MaxValue: Double = 100.0
}
