package dev.krysztal.casualtiesbelow.config.sections

import java.lang.Boolean
import java.lang.Double

import dev.krysztal.casualtiesbelow.config.FormulaConfigValue
import dev.krysztal.casualtiesbelow.physiology.temperature.TemperatureCalc

import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

private[config] final case class TemperatureValues(
    tauAirMinutes: ConfigValue[Double],
    immersionRateMultiplier: ConfigValue[Double],
    comfortLowCelsius: ConfigValue[Double],
    comfortHighCelsius: ConfigValue[Double],
    comfortSlope: ConfigValue[Double],
    penaltyBandLowCelsius: ConfigValue[Double],
    penaltyBandHighCelsius: ConfigValue[Double],
    coldConsciousnessSlopePerDegree: ConfigValue[Double],
    hotConsciousnessSlopePerDegree: ConfigValue[Double],
    coldImmuneDrainPerDegreePerMinute: ConfigValue[Double],
    hotImmuneDrainPerDegreePerMinute: ConfigValue[Double],
    sweatCoreTempThreshold: ConfigValue[Double],
    sweatWetnessPerSecond: ConfigValue[Double],
    sweatDirtinessMultiplier: ConfigValue[Double],
    evaporationCoolingPerMinute: ConfigValue[Double],
    exerciseHeatPerExhaustionPerSecond: ConfigValue[Double],
    onFireHeatPerMinute: ConfigValue[Double],
    lavaContactHeatPerMinute: ConfigValue[Double],
    heatSourceBlockHeatPerMinute: ConfigValue[Double],
    fireDryingBonusDegrees: ConfigValue[Double],
    immersionWetnessPerSecond: ConfigValue[Double],
    rainWetnessPerSecond: ConfigValue[Double],
    biomeMappingFormula: FormulaConfigValue,
    comfortBandFormula: FormulaConfigValue,
    effectiveTemperatureFormula: FormulaConfigValue,
    temperatureConsciousnessCeilingFormula: FormulaConfigValue,
    dryingCurveFormula: FormulaConfigValue,
    wetnessCollapseFormula: FormulaConfigValue,
    temperatureOverlayEnabled: ConfigValue[Boolean],
    frostOverlayStartCelsius: ConfigValue[Double],
    frostOverlayFullSpanCelsius: ConfigValue[Double],
    frostOverlayMaxStrength: ConfigValue[Double],
    heatOverlayStartCelsius: ConfigValue[Double],
    heatOverlayFullSpanCelsius: ConfigValue[Double],
    heatOverlayMaxStrength: ConfigValue[Double]
)

private[config] object TemperatureValues {

  def define(b: ModConfigSpec.Builder): TemperatureValues = {
    b.push("temperature")
    val s = TemperatureValues(
      tauAirMinutes = b
        .comment(
          "Time constant (minutes) of core-temperature approach in still air: every tau, the",
          "remaining gap to the equilibrium closes by ~63%. Initial placeholder, pending calibration."
        )
        .defineInRange("tauAirMinutes", 3.0, 0.1, 60.0, classOf[Double]),
      immersionRateMultiplier = b
        .comment(
          "Multiplier on the approach rate while immersed in water (water conducts heat far better",
          "than air). Initial placeholder, pending calibration."
        )
        .defineInRange("immersionRateMultiplier", 2.0, 1.0, 10.0, classOf[Double]),
      comfortLowCelsius = b
        .comment(
          "Lower bound of the comfort band (°C): at or above it, the equilibrium core temperature is",
          "normal body temperature. Initial placeholder, pending calibration."
        )
        .defineInRange("comfortLowCelsius", 10.0, -50.0, 37.0, classOf[Double]),
      comfortHighCelsius = b
        .comment(
          "Upper bound of the comfort band (°C): at or below it, the equilibrium core temperature is",
          "normal body temperature. Initial placeholder, pending calibration."
        )
        .defineInRange("comfortHighCelsius", 28.0, -50.0, 80.0, classOf[Double]),
      comfortSlope = b
        .comment(
          "How strongly the equilibrium core temperature deviates per °C of apparent temperature",
          "outside the comfort band. Initial placeholder, pending calibration."
        )
        .defineInRange("comfortSlope", 0.3, 0.0, 1.0, classOf[Double]),
      penaltyBandLowCelsius = b
        .comment(
          "Lower bound (°C) of the body-temperature penalty band: below it, consciousness is",
          "capped and immune health drains. Initial placeholder, pending calibration."
        )
        .defineInRange("penaltyBandLowCelsius", 35.0, 0.0, 37.0, classOf[Double]),
      penaltyBandHighCelsius = b
        .comment(
          "Upper bound (°C) of the body-temperature penalty band: above it, consciousness is capped",
          "and immune health drains. Initial placeholder, pending calibration."
        )
        .defineInRange("penaltyBandHighCelsius", 39.5, 37.0, 45.0, classOf[Double]),
      coldConsciousnessSlopePerDegree = b
        .comment(
          "Consciousness-ceiling reduction per °C of cold-side deviation below the penalty band.",
          "Initial placeholder, pending calibration."
        )
        .defineInRange("coldConsciousnessSlopePerDegree", 5.0, 0.0, 50.0, classOf[Double]),
      hotConsciousnessSlopePerDegree = b
        .comment(
          "Consciousness-ceiling reduction per °C of hot-side deviation above the penalty band.",
          "Initial placeholder, pending calibration."
        )
        .defineInRange("hotConsciousnessSlopePerDegree", 5.0, 0.0, 50.0, classOf[Double]),
      coldImmuneDrainPerDegreePerMinute = b
        .comment(
          "Immune-health drain per minute per °C of cold-side deviation below the penalty band",
          "(cold suppresses immunity harder than heat). Initial placeholder, pending calibration."
        )
        .defineInRange("coldImmuneDrainPerDegreePerMinute", 1.0, 0.0, 20.0, classOf[Double]),
      hotImmuneDrainPerDegreePerMinute = b
        .comment(
          "Immune-health drain per minute per °C of hot-side deviation above the penalty band.",
          "Initial placeholder, pending calibration."
        )
        .defineInRange("hotImmuneDrainPerDegreePerMinute", 0.5, 0.0, 20.0, classOf[Double]),
      sweatCoreTempThreshold = b
        .comment(
          "Core temperature (°C) above which exertion produces sweat (wetness). Initial placeholder,",
          "pending calibration."
        )
        .defineInRange("sweatCoreTempThreshold", 37.0, 30.0, 45.0, classOf[Double]),
      sweatWetnessPerSecond = b
        .comment(
          "Wetness produced per second at full sprint exertion while the core temperature is above",
          "the sweat threshold. Initial placeholder, pending calibration."
        )
        .defineInRange("sweatWetnessPerSecond", 0.15, 0.0, 1.0, classOf[Double]),
      sweatDirtinessMultiplier = b
        .comment(
          "Multiplier on passive dirtiness accrual while sweating (sweat gathers grime). Stacked",
          "multiplicatively with the other situational multipliers. Initial placeholder, pending",
          "calibration."
        )
        .defineInRange("sweatDirtinessMultiplier", 1.5, 1.0, 10.0, classOf[Double]),
      evaporationCoolingPerMinute = b
        .comment(
          "Maximum evaporative cooling (°C/min) at full wetness in fully dry air; scales with wetness",
          "and air dryness (1 - downfall). Initial placeholder, pending calibration."
        )
        .defineInRange("evaporationCoolingPerMinute", 0.3, 0.0, 5.0, classOf[Double]),
      exerciseHeatPerExhaustionPerSecond = b
        .comment(
          "Core-temperature gain (°C/min) per unit of vanilla exhaustion accumulated per second.",
          "Initial placeholder, pending calibration."
        )
        .defineInRange("exerciseHeatPerExhaustionPerSecond", 0.9, 0.0, 10.0, classOf[Double]),
      onFireHeatPerMinute = b
        .comment(
          "Direct contact heat (°C/min) while on fire or inside a fire block. Initial placeholder,",
          "pending calibration."
        )
        .defineInRange("onFireHeatPerMinute", 3.0, 0.0, 100.0, classOf[Double]),
      lavaContactHeatPerMinute = b
        .comment(
          "Direct contact heat (°C/min) while touching lava. Initial placeholder, pending calibration."
        )
        .defineInRange("lavaContactHeatPerMinute", 10.0, 0.0, 1000.0, classOf[Double]),
      heatSourceBlockHeatPerMinute = b
        .comment(
          "Direct contact heat (°C/min) while standing on a heat-source block (magma block, lit",
          "campfire). Initial placeholder, pending calibration."
        )
        .defineInRange("heatSourceBlockHeatPerMinute", 1.5, 0.0, 100.0, classOf[Double]),
      fireDryingBonusDegrees = b
        .comment(
          "Apparent-temperature bonus (°C) fed to the drying curve while on fire; dries wetness in",
          "seconds. Only affects drying, never core temperature. Initial placeholder, pending",
          "calibration."
        )
        .defineInRange("fireDryingBonusDegrees", 60.0, 0.0, 1000.0, classOf[Double]),
      immersionWetnessPerSecond = b
        .comment(
          "Wetness gain per second while immersed in water. Initial placeholder, pending calibration."
        )
        .defineInRange("immersionWetnessPerSecond", 0.5, 0.0, 1.0, classOf[Double]),
      rainWetnessPerSecond = b
        .comment(
          "Wetness gain per second while exposed to rain. Initial placeholder, pending calibration."
        )
        .defineInRange("rainWetnessPerSecond", 0.02, 0.0, 1.0, classOf[Double]),
      biomeMappingFormula = new FormulaConfigValue(
        b,
        "biomeMappingFormula",
        TemperatureCalc.BiomeMappingFormulaDefault,
        List("t"),
        comment = Seq(
          "Maps the vanilla biome temperature to apparent environmental temperature (°C), compiled",
          "with EvalEx. Available variable: t (height-adjusted vanilla biome temperature). The default",
          "anchors the rain/snow line 0.15 to 0°C and desert 2.0 to 40°C. Initial placeholder,",
          "pending calibration. Invalid formulas are rejected and corrected to the default.",
          "Hot-reloaded on file change."
        )
      ),
      comfortBandFormula = new FormulaConfigValue(
        b,
        "comfortBandFormula",
        TemperatureCalc.ComfortBandFormulaDefault,
        List("t", "low", "high", "slope"),
        comment = Seq(
          "Maps apparent temperature (°C) to the equilibrium core temperature, compiled with EvalEx.",
          "Available variables: t (apparent temperature), low/high (comfort band bounds), slope.",
          "Inside the band the equilibrium is normal body temperature (37). Initial placeholder,",
          "pending calibration. Invalid formulas are rejected and corrected to the default.",
          "Hot-reloaded on file change."
        )
      ),
      effectiveTemperatureFormula = new FormulaConfigValue(
        b,
        "effectiveTemperatureFormula",
        TemperatureCalc.EffectiveTemperatureFormulaDefault,
        List("t", "i"),
        comment = Seq(
          "Applies armor insulation to the equilibrium core temperature, compiled with EvalEx.",
          "Available variables: t (equilibrium before armor), i (effective insulation after wetness",
          "collapse). Insulation is gated to the cold side (t <= 37): in heat it does nothing, so",
          "clothing cannot make a hot environment feel colder - the heat-side clothing property is",
          "the dissipation-block coefficient. Initial placeholder, pending calibration. Invalid",
          "formulas are rejected and corrected to the default. Hot-reloaded on file change."
        )
      ),
      temperatureConsciousnessCeilingFormula = new FormulaConfigValue(
        b,
        "temperatureConsciousnessCeilingFormula",
        TemperatureCalc.TemperatureConsciousnessCeilingFormulaDefault,
        List("coldDev", "hotDev", "coldSlope", "hotSlope"),
        comment = Seq(
          "Consciousness ceiling from body-temperature deviation outside the penalty band, compiled",
          "with EvalEx. Available variables: coldDev/hotDev (°C outside the penalty band on each",
          "side), coldSlope/hotSlope (ceiling reduction per °C per side). Together with the",
          "per-minute immune drains this is the penalty band: low heatstroke and hypothermia press",
          "consciousness instead of dealing damage. Initial placeholder, pending calibration. Invalid",
          "formulas are rejected and corrected to the default. Hot-reloaded on file change."
        )
      ),
      dryingCurveFormula = new FormulaConfigValue(
        b,
        "dryingCurveFormula",
        TemperatureCalc.DryingCurveFormulaDefault,
        List("t"),
        comment = Seq(
          "Wetness lost per second from the drying temperature, compiled with EvalEx. Available",
          "variable: t (drying temperature = apparent temperature + bonus, e.g. while on fire). The",
          "result is further scaled by air dryness (1 - downfall) in code. NOTE: this EvalEx",
          "configuration has no exp() function; the exponential is written as e's numeric power.",
          "Initial placeholder, pending calibration. Invalid formulas are rejected and corrected to the default.",
          "Hot-reloaded on file change."
        )
      ),
      wetnessCollapseFormula = new FormulaConfigValue(
        b,
        "wetnessCollapseFormula",
        TemperatureCalc.WetnessCollapseFormulaDefault,
        List("wetness"),
        comment = Seq(
          "Fraction of armor thermal coefficients (insulation and dissipation block) that survives at",
          "a given wetness, compiled with EvalEx. Available variable: wetness (0..1). Wet armor both",
          "stops insulating and stops trapping sweat. Initial placeholder, pending calibration.",
          "Invalid formulas are rejected and corrected to the default. Hot-reloaded on file change."
        )
      ),
      temperatureOverlayEnabled = b
        .comment(
          "Temperature screen effects: a vitals post-shader axis. Cold side grows a frost overlay",
          "inward from the screen edges (vanilla's powder-snow texture with a spatial mask); hot side",
          "adds heat-haze wobble and a warm edge tint. Client-side presentation only; no gameplay",
          "effect."
        )
        .define("temperatureOverlayEnabled", true),
      frostOverlayStartCelsius = b
        .comment(
          "Core body temperature (°C) at which the frost overlay starts.",
          "Initial placeholder, pending calibration. Client-side presentation only."
        )
        .defineInRange("frostOverlayStartCelsius", 35.0, 20.0, 37.0, classOf[Double]),
      frostOverlayFullSpanCelsius = b
        .comment(
          "Degrees below frostOverlayStartCelsius at which the frost overlay reaches its maximum",
          "strength. Initial placeholder, pending calibration. Client-side presentation only."
        )
        .defineInRange("frostOverlayFullSpanCelsius", 6.0, 1.0, 20.0, classOf[Double]),
      frostOverlayMaxStrength = b
        .comment(
          "Maximum frost overlay strength (0..1) reached at the full span below the onset.",
          "Initial placeholder, pending calibration. Client-side presentation only."
        )
        .defineInRange("frostOverlayMaxStrength", 0.85, 0.0, 1.0, classOf[Double]),
      heatOverlayStartCelsius = b
        .comment(
          "Core body temperature (°C) at which the heat overlay (haze + warm tint) starts.",
          "Initial placeholder, pending calibration. Client-side presentation only."
        )
        .defineInRange("heatOverlayStartCelsius", 39.5, 37.0, 45.0, classOf[Double]),
      heatOverlayFullSpanCelsius = b
        .comment(
          "Degrees above heatOverlayStartCelsius at which the heat overlay reaches its maximum",
          "strength — anchored so the maximum lands on the terminal-band edge (heatstroke).",
          "Initial placeholder, pending calibration. Client-side presentation only."
        )
        .defineInRange("heatOverlayFullSpanCelsius", 2.5, 0.5, 10.0, classOf[Double]),
      heatOverlayMaxStrength = b
        .comment(
          "Maximum heat overlay strength (0..1) reached at the full span above the onset.",
          "Initial placeholder, pending calibration. Client-side presentation only."
        )
        .defineInRange("heatOverlayMaxStrength", 0.85, 0.0, 1.0, classOf[Double])
    )
    b.pop()
    s
  }
}
