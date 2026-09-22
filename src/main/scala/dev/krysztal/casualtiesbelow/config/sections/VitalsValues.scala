package dev.krysztal.casualtiesbelow.config.sections

import java.lang.Double

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent

import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

private[config] final case class VitalsValues(
    startingHealth: ConfigValue[Double],
    startingConsciousness: ConfigValue[Double],
    consciousnessDimThreshold: ConfigValue[Double],
    consciousnessFloor: ConfigValue[Double],
    consciousnessKnockoutThreshold: ConfigValue[Double],
    consciousnessIncapacitationStartThreshold: ConfigValue[Double],
    consciousnessMaxDimOpacity: ConfigValue[Double],
    consciousnessMaxBlurStrength: ConfigValue[Double],
    bloodOxygenDepletionPerTick: ConfigValue[Double],
    bloodOxygenRecoveryPerTick: ConfigValue[Double],
    bloodOxygenHypoxiaThreshold: ConfigValue[Double],
    consciousnessOxygenCapMultiplier: ConfigValue[Double],
    consciousnessRecoveryOxygenThreshold: ConfigValue[Double],
    consciousnessRecoveryPerTick: ConfigValue[Double],
    consciousnessWakeThreshold: ConfigValue[Double],
    maxBloodVolume: ConfigValue[Double],
    fullOxygenBloodFraction: ConfigValue[Double],
    bloodDesaturationStartFraction: ConfigValue[Double],
    bloodFullDesaturationFraction: ConfigValue[Double],
    fedBloodRegenPerTick: ConfigValue[Double],
    maxImmuneHealth: ConfigValue[Double]
)

private[config] object VitalsValues {

  def define(b: ModConfigSpec.Builder): VitalsValues = {
    b.push("vitals")
    val s = VitalsValues(
      startingHealth = b
        .comment("Overall health a player starts with.")
        .gameRestart()
        .defineInRange("startingHealth", 100.0, 0.0, 100.0, classOf[Double]),
      startingConsciousness = b
        .comment("Consciousness a player starts with.")
        .gameRestart()
        .defineInRange("startingConsciousness", 100.0, 0.0, 100.0, classOf[Double]),
      consciousnessDimThreshold = b
        .comment(
          "Consciousness below which the view starts to dim (client-side display effect only)."
        )
        .defineInRange("consciousnessDimThreshold", 50.0, 0.0, 100.0, classOf[Double]),
      consciousnessFloor = b
        .comment(
          "Ordinary minimum consciousness value. Knockout is controlled independently by",
          "consciousnessKnockoutThreshold; this floor only bounds stable physiological progression.",
          "Pain shock is the bounded exception: collapse sets consciousness to literal zero and its",
          "recovery phase permits the scalar to rise from zero before normal floor rules resume."
        )
        .defineInRange("consciousnessFloor", 10.0, 0.0, 100.0, classOf[Double]),
      consciousnessKnockoutThreshold = b
        .comment(
          "Consciousness at or below which an otherwise awake player becomes unconscious.",
          "At use time it is kept no lower than consciousnessFloor. Keep the wake threshold above",
          "this value to preserve hysteresis."
        )
        .defineInRange("consciousnessKnockoutThreshold", 30.0, 0.0, 100.0, classOf[Double]),
      consciousnessIncapacitationStartThreshold = b
        .comment(
          "Consciousness below which blackout and movement slowdown ramp in linearly,",
          "reaching full effect at consciousnessKnockoutThreshold. Must exceed that threshold."
        )
        .defineInRange(
          "consciousnessIncapacitationStartThreshold",
          50.0,
          0.0,
          100.0,
          classOf[Double]
        ),
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
          "The effect shares the dimming ramp below consciousnessDimThreshold; 0 disables it."
        )
        .defineInRange("consciousnessMaxBlurStrength", 0.99, 0.0, 1.0, classOf[Double]),
      bloodOxygenDepletionPerTick = b
        .comment(
          "Blood oxygen lost per tick only after the vanilla air supply is fully exhausted.",
          "Respiration and Water Breathing retain their normal effects by delaying or preventing",
          "that point. At the default rate, a healthy reserve takes 12.5 seconds of exhausted",
          "air to fall from 100 to zero."
        )
        .defineInRange("bloodOxygenDepletionPerTick", 0.4, 0.0, 100.0, classOf[Double]),
      bloodOxygenRecoveryPerTick = b
        .comment(
          "Blood oxygen restored per tick while vanilla air remains available.",
          "Recovery can never exceed the capacity allowed by the current blood volume."
        )
        .defineInRange("bloodOxygenRecoveryPerTick", 0.8, 0.0, 100.0, classOf[Double]),
      bloodOxygenHypoxiaThreshold = b
        .comment(
          "Blood oxygen below which client medical displays mark severe hypoxia.",
          "Consciousness itself follows its oxygen-derived hard ceiling rather than this warning value."
        )
        .defineInRange("bloodOxygenHypoxiaThreshold", 50.0, 0.0, 100.0, classOf[Double]),
      consciousnessOxygenCapMultiplier = b
        .comment(
          "Multiplier from current blood oxygen to the hard consciousness ceiling.",
          "The default 1.2 means 25 oxygen caps consciousness at 30 and 50 caps it at 60."
        )
        .defineInRange("consciousnessOxygenCapMultiplier", 1.2, 0.0, 100.0, classOf[Double]),
      consciousnessRecoveryOxygenThreshold = b
        .comment(
          "Blood oxygen at or above which consciousness can recover and waking is no longer blocked.",
          "Below this value, the oxygen-derived hard ceiling still applies but consciousness cannot rise."
        )
        .defineInRange("consciousnessRecoveryOxygenThreshold", 75.0, 0.0, 100.0, classOf[Double]),
      consciousnessRecoveryPerTick = b
        .comment(
          "Consciousness restored per tick while blood oxygen is above its recovery threshold.",
          "The default 0.2 restores 4 consciousness per second."
        )
        .defineInRange("consciousnessRecoveryPerTick", 0.2, 0.0, 100.0, classOf[Double]),
      consciousnessWakeThreshold = b
        .comment(
          "Consciousness at which an unconscious player can wake once no active cause blocks waking.",
          "Values below it form a hysteresis band with consciousnessKnockoutThreshold.",
          "The minimum is 0.000001 so waking always requires positive consciousness."
        )
        .defineInRange(
          "consciousnessWakeThreshold",
          40.0,
          VitalsComponent.MinimumWakeThreshold,
          VitalsComponent.MaxValue,
          classOf[Double]
        ),
      maxBloodVolume = b
        .comment(
          "Total blood volume of a player, in mL; bleeding drains it and reaching zero is fatal."
        )
        .gameRestart()
        .defineInRange("maxBloodVolume", 5000.0, 100.0, 100000.0, classOf[Double]),
      fullOxygenBloodFraction = b
        .comment(
          "Fraction of healthy maximum blood volume that can still carry 100 blood oxygen.",
          "Below this point oxygen capacity falls linearly with blood volume; the default 0.6 means",
          "3000 mL and above retain full capacity, while 1500 mL can carry at most 50 oxygen."
        )
        .defineInRange("fullOxygenBloodFraction", 0.6, 0.000001, 1.0, classOf[Double]),
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
      fedBloodRegenPerTick = b
        .comment(
          "Blood volume regenerated per tick while well-fed (same food threshold as immune",
          "regeneration), capped by the effective maximum blood volume. At the default 0.05 mL/tick,",
          "blood recovers at 1 mL/second; a survivor of sepsis or heavy bleeding has to eat well."
        )
        .defineInRange("fedBloodRegenPerTick", 0.05, 0.0, 100.0, classOf[Double]),
      maxImmuneHealth = b
        .comment(
          "Maximum (and starting) immune health. With the default infection rates, the break-even",
          "point where the immune system exactly matches infection spread is 120 out of 200."
        )
        .gameRestart()
        .defineInRange("maxImmuneHealth", 200.0, 1.0, 10000.0, classOf[Double])
    )
    b.pop()
    s
  }
}
