package dev.krysztal.casualtiesbelow.config.sections

import java.lang.Boolean
import java.lang.Double

import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

/** Client-side presentation parameters collected from every gameplay section, so players tuning
  * mechanics are not wading through vignette and overlay knobs. Nothing here changes gameplay; the
  * values are still server-authoritative (COMMON spec) so a server can enforce its look.
  */
private[config] final case class VisualsValues(
    consciousnessMaxDimOpacity: ConfigValue[Double],
    consciousnessMaxBlurStrength: ConfigValue[Double],
    bloodDesaturationStartFraction: ConfigValue[Double],
    bloodFullDesaturationFraction: ConfigValue[Double],
    nauseaVignetteMaxOpacity: ConfigValue[Double],
    dirtinessBandGrimy: ConfigValue[Double],
    dirtinessBandFilthy: ConfigValue[Double],
    dirtinessBandSqualid: ConfigValue[Double],
    grimeVignetteMaxOpacity: ConfigValue[Double],
    shockVisualStartLoad: ConfigValue[Double],
    shockVisualMaxStrength: ConfigValue[Double],
    shockVisualPulseStrength: ConfigValue[Double],
    shockVisualNoiseStrength: ConfigValue[Double],
    temperatureOverlayEnabled: ConfigValue[Boolean],
    frostOverlayStartCelsius: ConfigValue[Double],
    frostOverlayFullSpanCelsius: ConfigValue[Double],
    frostOverlayMaxStrength: ConfigValue[Double],
    heatOverlayStartCelsius: ConfigValue[Double],
    heatOverlayFullSpanCelsius: ConfigValue[Double],
    heatOverlayMaxStrength: ConfigValue[Double]
)

private[config] object VisualsValues {

  def define(b: ModConfigSpec.Builder): VisualsValues = {
    b.push("visuals")
    val s = VisualsValues(
      consciousnessMaxDimOpacity = b
        .comment(
          "Strongest awake dimming opacity (0.0-1.0), approached near the consciousness floor.",
          "Edge darkening is applied in addition to the full-screen haze; 0 disables awake dimming.",
          "Unconscious blackout remains fully opaque. Dimming and blur gently pulse while active."
        )
        .defineInRange("consciousnessMaxDimOpacity", 0.55, 0.0, 1.0, classOf[Double]),
      consciousnessMaxBlurStrength = b
        .comment(
          "Strongest zoom blur and double-vision strength (0.0-1.0), reached at zero consciousness.",
          "The effect shares the dimming ramp below the impairment start threshold; 0 disables it."
        )
        .defineInRange("consciousnessMaxBlurStrength", 0.99, 0.0, 1.0, classOf[Double]),
      bloodDesaturationStartFraction = b
        .comment(
          "Fraction of healthy maximum blood volume below which the world starts losing color.",
          "The effect is client-side presentation only."
        )
        .defineInRange("bloodDesaturationStartFraction", 0.9, 0.0, 1.0, classOf[Double]),
      bloodFullDesaturationFraction = b
        .comment(
          "Fraction of healthy maximum blood volume at or below which the world is fully grayscale.",
          "Keep this below bloodDesaturationStartFraction for a gradual transition."
        )
        .defineInRange("bloodFullDesaturationFraction", 0.3, 0.0, 1.0, classOf[Double]),
      nauseaVignetteMaxOpacity = b
        .comment(
          "Strongest nausea edge-darkening strength (0.0-1.0), reached at maximum discomfort.",
          "This effect does not add low-consciousness blur or pulsing; 0 disables it."
        )
        .defineInRange("nauseaVignetteMaxOpacity", 0.35, 0.0, 1.0, classOf[Double]),
      dirtinessBandGrimy = b
        .comment(
          "Dirtiness of the grimy display band: the grime vignette starts appearing here.",
          "Display only; no mechanic reads the bands."
        )
        .defineInRange("dirtinessBandGrimy", 30.0, 0.0, 10000.0, classOf[Double]),
      dirtinessBandFilthy = b
        .comment("Dirtiness of the filthy display band. Display only.")
        .defineInRange("dirtinessBandFilthy", 60.0, 0.0, 10000.0, classOf[Double]),
      dirtinessBandSqualid = b
        .comment("Dirtiness of the squalid display band. Display only.")
        .defineInRange("dirtinessBandSqualid", 85.0, 0.0, 10000.0, classOf[Double]),
      grimeVignetteMaxOpacity = b
        .comment(
          "Strongest grime vignette opacity (0.0-1.0), ramping from dirtinessBandGrimy to the",
          "dirtiness maximum; brown-toned, distinct from the nausea green. 0 disables it."
        )
        .defineInRange("grimeVignetteMaxOpacity", 0.55, 0.0, 1.0, classOf[Double]),
      shockVisualStartLoad = b
        .comment(
          "Client-local shock load at which the warm peripheral warning starts fading in.",
          "This display value is not synchronized from dedicated servers."
        )
        .defineInRange("shockVisualStartLoad", 0.0, 0.0, 100.0, classOf[Double]),
      shockVisualMaxStrength = b
        .comment(
          "Client-local maximum warm peripheral warning strength (0.0-1.0).",
          "Set to zero to disable the pain-shock warning without changing gameplay."
        )
        .defineInRange("shockVisualMaxStrength", 1.0, 0.0, 1.0, classOf[Double]),
      shockVisualPulseStrength = b
        .comment(
          "Client-local depth of the slow warning pulse during the final half of the load ramp.",
          "Set to zero for a static peripheral warning."
        )
        .defineInRange("shockVisualPulseStrength", 0.2, 0.0, 1.0, classOf[Double]),
      shockVisualNoiseStrength = b
        .comment(
          "Client-local brightness variation of the animated static at the warning edge.",
          "The default is clearly visible but remains peripheral; zero disables it independently."
        )
        .defineInRange("shockVisualNoiseStrength", 0.25, 0.0, 0.5, classOf[Double]),
      temperatureOverlayEnabled = b
        .comment(
          "Temperature screen effects: a vitals post-shader axis. Cold side grows a frost overlay",
          "inward from the screen edges (vanilla's powder-snow texture with a spatial mask); hot side",
          "adds heat-haze wobble and a warm edge tint."
        )
        .define("temperatureOverlayEnabled", true),
      frostOverlayStartCelsius = b
        .comment(
          "Core body temperature (°C) at which the frost overlay starts.",
          "Initial placeholder, pending calibration."
        )
        .defineInRange("frostOverlayStartCelsius", 35.0, 20.0, 37.0, classOf[Double]),
      frostOverlayFullSpanCelsius = b
        .comment(
          "Degrees below frostOverlayStartCelsius at which the frost overlay reaches its maximum",
          "strength. Initial placeholder, pending calibration."
        )
        .defineInRange("frostOverlayFullSpanCelsius", 6.0, 1.0, 20.0, classOf[Double]),
      frostOverlayMaxStrength = b
        .comment(
          "Maximum frost overlay strength (0..1) reached at the full span below the onset.",
          "Initial placeholder, pending calibration."
        )
        .defineInRange("frostOverlayMaxStrength", 0.85, 0.0, 1.0, classOf[Double]),
      heatOverlayStartCelsius = b
        .comment(
          "Core body temperature (°C) at which the heat overlay (haze + warm tint) starts.",
          "Initial placeholder, pending calibration."
        )
        .defineInRange("heatOverlayStartCelsius", 39.5, 37.0, 45.0, classOf[Double]),
      heatOverlayFullSpanCelsius = b
        .comment(
          "Degrees above heatOverlayStartCelsius at which the heat overlay reaches its maximum",
          "strength — anchored so the maximum lands on the terminal-band edge (heatstroke).",
          "Initial placeholder, pending calibration."
        )
        .defineInRange("heatOverlayFullSpanCelsius", 2.5, 0.5, 10.0, classOf[Double]),
      heatOverlayMaxStrength = b
        .comment(
          "Maximum heat overlay strength (0..1) reached at the full span above the onset.",
          "Initial placeholder, pending calibration."
        )
        .defineInRange("heatOverlayMaxStrength", 0.85, 0.0, 1.0, classOf[Double])
    )
    b.pop()
    s
  }
}
