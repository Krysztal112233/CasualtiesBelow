package dev.krysztal.casualtiesbelow.physiology.discomfort

/** Pure formula steps extracted from [[Discomfort]] so every stage can be unit-tested without a
  * running game: the progression reads config and world state, then delegates the math here.
  */
object DiscomfortCalc {

  /** Ordinary decay: fast while merely queasy (below the nausea threshold), slow once actually
    * sick, so high discomfort asks for active resolution (or a vomit) instead of being waited out.
    * Withdrawal owns discomfort evolution while active, freezing ordinary decay entirely.
    */
  def nextAfterOrdinaryDecay(
      discomfort: Double,
      withdrawalActive: Boolean,
      nauseaThreshold: Double,
      lowDecayPerSecond: Double,
      highDecayPerSecond: Double
  ): Double = {
    if (withdrawalActive) discomfort
    else {
      val rate =
        if (discomfort < nauseaThreshold) lowDecayPerSecond else highDecayPerSecond
      (discomfort - rate.max(0.0) / 20.0).max(0.0)
    }
  }

  /** Per-tick chance ramp: zero at or below `threshold`, then rising linearly from `minChance`
    * toward `maxChance` as discomfort approaches `maxDiscomfort` (clamped beyond it). Pure: the
    * caller supplies a uniform roll and compares.
    */
  def chancePerTick(
      discomfort: Double,
      threshold: Double,
      minChance: Double,
      maxChance: Double,
      maxDiscomfort: Double
  ): Double = {
    if (discomfort <= threshold) 0.0
    else {
      val progress =
        if (maxDiscomfort <= threshold) 1.0
        else ((discomfort - threshold) / (maxDiscomfort - threshold)).max(0.0).min(1.0)
      minChance + (math.max(maxChance, minChance) - minChance) * progress
    }
  }

  /** Uniform sample around `mean` with a spread proportional to the mean; `roll` is a uniform
    * sample in [0, 1).
    */
  def uniformSample(mean: Double, spreadFraction: Double, roll: Double): Double =
    mean + (roll * 2.0 - 1.0) * mean * spreadFraction

  /** Gaussian sample around `mean` with a spread proportional to the mean; `gaussian` is a
    * standard-normal sample.
    */
  def gaussianSample(mean: Double, spreadFraction: Double, gaussian: Double): Double =
    mean + gaussian * mean * spreadFraction
}
