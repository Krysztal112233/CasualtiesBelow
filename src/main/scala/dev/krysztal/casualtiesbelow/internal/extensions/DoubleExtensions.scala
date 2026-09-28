package dev.krysztal.casualtiesbelow.internal.extensions

/** Numeric guard/clamp extensions shared by physiology math.
  *
  * Each extension is a fixed contract on non-finite input: NaN always maps to 0.0, while the
  * positive-infinity policy differs per method and is part of its contract.
  */
private[casualtiesbelow] object DoubleExtensions {

  extension (value: Double) {

    /** Clamps into `[0, maximum]`, sanitizing the maximum itself; positive infinity maps to the
      * sanitized maximum.
      */
    def bounded(maximum: Double): Double = {
      val limit = maximum.nonNegative
      if (value == Double.PositiveInfinity) limit
      else if (value.isFinite) value.max(0.0).min(limit)
      else 0.0
    }

    /** Clamps into `[0, 1]`; positive infinity maps to 1.0. */
    def boundedFraction: Double = bounded(1.0)

    /** Clamps into `[0, Double.MaxValue]`; positive infinity maps to `Double.MaxValue`. Used for
      * amounts and rates where infinity must not collapse onto a concrete cap.
      */
    def nonNegative: Double = {
      if (value == Double.PositiveInfinity) Double.MaxValue
      else if (value.isFinite) value.max(0.0)
      else 0.0
    }

    /** Returns the value when finite, else 0.0; no clamping. */
    def finiteOrZero: Double = if (value.isFinite) value else 0.0

    /** Gaussian sample around the value as mean, with a spread proportional to `|mean|`; `gaussian`
      * is a standard-normal sample. Not floored: clamp at the call site when needed.
      */
    def gaussianSample(spreadFraction: Double, gaussian: Double): Double =
      value + gaussian * math.abs(value) * spreadFraction

    /** Uniform jitter around the value: `value × (1 ± jitterFraction)`; `roll` is a uniform sample
      * in [0, 1). Not floored: clamp at the call site when needed.
      */
    def jittered(jitterFraction: Double, roll: Double): Double =
      value * (1.0 + (roll * 2.0 - 1.0) * jitterFraction)

    /** Linear ramp position of the value between `start` (0) and `full` (1), clamped to [0, 1]; a
      * ramp narrower than 1 is treated as width 1.
      */
    def ramp01(start: Double, full: Double): Double =
      ((value - start) / (full - start).max(1.0)).max(0.0).min(1.0)

    /** Bit-level equality, distinguishing `0.0` from `-0.0` and treating NaN as equal to itself. */
    def sameBits(other: Double): Boolean = {
      java.lang.Double.doubleToLongBits(value) == java.lang.Double.doubleToLongBits(other)
    }
  }
}
