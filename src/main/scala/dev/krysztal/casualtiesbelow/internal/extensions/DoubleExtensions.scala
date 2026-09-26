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

    /** Bit-level equality, distinguishing `0.0` from `-0.0` and treating NaN as equal to itself. */
    def sameBits(other: Double): Boolean = {
      java.lang.Double.doubleToLongBits(value) == java.lang.Double.doubleToLongBits(other)
    }
  }
}
