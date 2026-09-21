package dev.krysztal.casualtiesbelow.ui

/** Pure presentation curves for the temperature post-shader axis ([[VitalsPostEffect]] uploads the
  * results; the shader only renders them).
  *
  * Cold side: a linear frost ramp — linearity mirrors vanilla's `getPercentFrozen` freeze curve —
  * while the spatial growth of the frost mask lives in `shaders/include/vitals/temperature.glsl`.
  */
private[casualtiesbelow] object TemperatureVisuals {

  /** Frost overlay strength 0..`maxStrength`: zero at or above `startCelsius`, rising linearly to
    * full strength at `startCelsius - fullSpanCelsius` and clamped beyond.
    */
  def frostStrength(
      bodyTemperature: Double,
      startCelsius: Double,
      fullSpanCelsius: Double,
      maxStrength: Double
  ): Double = {
    val span = math.max(fullSpanCelsius, 1.0e-6)
    val ramp = ((startCelsius - bodyTemperature) / span).max(0.0).min(1.0)
    maxStrength * ramp
  }
}
