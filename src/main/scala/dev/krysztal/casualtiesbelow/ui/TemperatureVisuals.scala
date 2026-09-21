package dev.krysztal.casualtiesbelow.ui

/** Pure presentation curves for the temperature post-shader axis ([[VitalsPostEffect]] uploads the
  * results; the shader only renders them).
  *
  * Linear ramps on both sides — linearity mirrors vanilla's `getPercentFrozen` freeze curve — while
  * the spatial/temporal character lives in `shaders/include/vitals/temperature.glsl` (frost mask
  * growth, heat haze wobble).
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

  /** Heat overlay strength 0..`maxStrength`: zero at or below `startCelsius` (the hot penalty-band
    * edge), rising linearly to full strength at `startCelsius + fullSpanCelsius` — anchored so full
    * strength lands on the terminal-band edge — and clamped beyond.
    */
  def heatStrength(
      bodyTemperature: Double,
      startCelsius: Double,
      fullSpanCelsius: Double,
      maxStrength: Double
  ): Double = {
    val span = math.max(fullSpanCelsius, 1.0e-6)
    val ramp = ((bodyTemperature - startCelsius) / span).max(0.0).min(1.0)
    maxStrength * ramp
  }
}
