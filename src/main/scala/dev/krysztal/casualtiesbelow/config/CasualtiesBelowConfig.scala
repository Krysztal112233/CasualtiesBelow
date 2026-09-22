package dev.krysztal.casualtiesbelow.config

import java.lang.Boolean
import java.lang.Double
import java.lang.Integer

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.physiology.discomfort.DiscomfortDistribution as Distribution
import dev.krysztal.casualtiesbelow.physiology.pain.TotalPainStrategy
import dev.krysztal.casualtiesbelow.physiology.temperature.TemperatureCalc

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

/** Common (server-authoritative) configuration, backed by Forge Config API Port. Values are written
  * to `config/casualtiesbelow-common.toml` and can be edited in-game via the ModMenu integration
  * provided by Forge Config API Port.
  */
object CasualtiesBelowConfig {

  private val CurrentPhysiologyBalanceVersion = 1
  private val Builder = new ModConfigSpec.Builder()

  private val PhysiologyBalanceVersion: ConfigValue[Integer] = Builder
    .comment(
      "Internal migration marker for physiology balance defaults. Do not edit manually."
    )
    .defineInRange(
      "physiologyBalanceVersion",
      0,
      0,
      CurrentPhysiologyBalanceVersion
    )

  Builder.push("vitals")
  val StartingHealth: ConfigValue[Double] = Builder
    .comment("Overall health a player starts with.")
    .gameRestart()
    .defineInRange("startingHealth", 100.0, 0.0, 100.0, classOf[Double])
  val StartingConsciousness: ConfigValue[Double] = Builder
    .comment("Consciousness a player starts with.")
    .gameRestart()
    .defineInRange("startingConsciousness", 100.0, 0.0, 100.0, classOf[Double])
  val ConsciousnessDimThreshold: ConfigValue[Double] = Builder
    .comment(
      "Consciousness below which the view starts to dim (client-side display effect only)."
    )
    .defineInRange("consciousnessDimThreshold", 50.0, 0.0, 100.0, classOf[Double])
  val ConsciousnessFloor: ConfigValue[Double] = Builder
    .comment(
      "Ordinary minimum consciousness value. Knockout is controlled independently by",
      "consciousnessKnockoutThreshold; this floor only bounds stable physiological progression.",
      "Pain shock is the bounded exception: collapse sets consciousness to literal zero and its",
      "recovery phase permits the scalar to rise from zero before normal floor rules resume."
    )
    .defineInRange("consciousnessFloor", 10.0, 0.0, 100.0, classOf[Double])
  val ConsciousnessKnockoutThreshold: ConfigValue[Double] = Builder
    .comment(
      "Consciousness at or below which an otherwise awake player becomes unconscious.",
      "At use time it is kept no lower than consciousnessFloor. Keep the wake threshold above",
      "this value to preserve hysteresis."
    )
    .defineInRange("consciousnessKnockoutThreshold", 30.0, 0.0, 100.0, classOf[Double])
  val ConsciousnessIncapacitationStartThreshold: ConfigValue[Double] = Builder
    .comment(
      "Consciousness below which blackout and movement slowdown ramp in linearly,",
      "reaching full effect at consciousnessKnockoutThreshold. Must exceed that threshold."
    )
    .defineInRange("consciousnessIncapacitationStartThreshold", 50.0, 0.0, 100.0, classOf[Double])
  val ConsciousnessMaxDimOpacity: ConfigValue[Double] = Builder
    .comment(
      "Strongest awake dimming opacity (0.0-1.0), approached near the consciousness floor.",
      "Edge darkening is applied in addition to the full-screen haze; 0 disables awake dimming.",
      "Unconscious blackout remains fully opaque. Dimming and blur gently pulse while active."
    )
    .defineInRange("consciousnessMaxDimOpacity", 0.55, 0.0, 1.0, classOf[Double])
  val ConsciousnessMaxBlurStrength: ConfigValue[Double] = Builder
    .comment(
      "Strongest zoom blur and double-vision strength (0.0-1.0), reached at zero consciousness.",
      "The effect shares the dimming ramp below consciousnessDimThreshold; 0 disables it."
    )
    .defineInRange("consciousnessMaxBlurStrength", 0.99, 0.0, 1.0, classOf[Double])
  val BloodOxygenDepletionPerTick: ConfigValue[Double] = Builder
    .comment(
      "Blood oxygen lost per tick only after the vanilla air supply is fully exhausted.",
      "Respiration and Water Breathing retain their normal effects by delaying or preventing",
      "that point. At the default rate, a healthy reserve takes 12.5 seconds of exhausted",
      "air to fall from 100 to zero."
    )
    .defineInRange("bloodOxygenDepletionPerTick", 0.4, 0.0, 100.0, classOf[Double])
  val BloodOxygenRecoveryPerTick: ConfigValue[Double] = Builder
    .comment(
      "Blood oxygen restored per tick while vanilla air remains available.",
      "Recovery can never exceed the capacity allowed by the current blood volume."
    )
    .defineInRange("bloodOxygenRecoveryPerTick", 0.8, 0.0, 100.0, classOf[Double])
  val BloodOxygenHypoxiaThreshold: ConfigValue[Double] = Builder
    .comment(
      "Blood oxygen below which client medical displays mark severe hypoxia.",
      "Consciousness itself follows its oxygen-derived hard ceiling rather than this warning value."
    )
    .defineInRange("bloodOxygenHypoxiaThreshold", 50.0, 0.0, 100.0, classOf[Double])
  val ConsciousnessOxygenCapMultiplier: ConfigValue[Double] = Builder
    .comment(
      "Multiplier from current blood oxygen to the hard consciousness ceiling.",
      "The default 1.2 means 25 oxygen caps consciousness at 30 and 50 caps it at 60."
    )
    .defineInRange("consciousnessOxygenCapMultiplier", 1.2, 0.0, 100.0, classOf[Double])
  val ConsciousnessRecoveryOxygenThreshold: ConfigValue[Double] = Builder
    .comment(
      "Blood oxygen at or above which consciousness can recover and waking is no longer blocked.",
      "Below this value, the oxygen-derived hard ceiling still applies but consciousness cannot rise."
    )
    .defineInRange("consciousnessRecoveryOxygenThreshold", 75.0, 0.0, 100.0, classOf[Double])
  val ConsciousnessRecoveryPerTick: ConfigValue[Double] = Builder
    .comment(
      "Consciousness restored per tick while blood oxygen is above its recovery threshold.",
      "The default 0.2 restores 4 consciousness per second."
    )
    .defineInRange("consciousnessRecoveryPerTick", 0.2, 0.0, 100.0, classOf[Double])
  val ConsciousnessWakeThreshold: ConfigValue[Double] = Builder
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
    )
  val MaxBloodVolume: ConfigValue[Double] = Builder
    .comment(
      "Total blood volume of a player, in mL; bleeding drains it and reaching zero is fatal."
    )
    .gameRestart()
    .defineInRange("maxBloodVolume", 5000.0, 100.0, 100000.0, classOf[Double])
  val FullOxygenBloodFraction: ConfigValue[Double] = Builder
    .comment(
      "Fraction of healthy maximum blood volume that can still carry 100 blood oxygen.",
      "Below this point oxygen capacity falls linearly with blood volume; the default 0.6 means",
      "3000 mL and above retain full capacity, while 1500 mL can carry at most 50 oxygen."
    )
    .defineInRange("fullOxygenBloodFraction", 0.6, 0.000001, 1.0, classOf[Double])
  val BloodDesaturationStartFraction: ConfigValue[Double] = Builder
    .comment(
      "Fraction of healthy maximum blood volume below which the world starts losing color.",
      "The effect is client-side presentation only."
    )
    .defineInRange("bloodDesaturationStartFraction", 0.9, 0.0, 1.0, classOf[Double])
  val BloodFullDesaturationFraction: ConfigValue[Double] = Builder
    .comment(
      "Fraction of healthy maximum blood volume at or below which the world is fully grayscale.",
      "Keep this below bloodDesaturationStartFraction for a gradual transition."
    )
    .defineInRange("bloodFullDesaturationFraction", 0.3, 0.0, 1.0, classOf[Double])
  val FedBloodRegenPerTick: ConfigValue[Double] = Builder
    .comment(
      "Blood volume regenerated per tick while well-fed (same food threshold as immune",
      "regeneration), capped by the effective maximum blood volume. At the default 0.05 mL/tick,",
      "blood recovers at 1 mL/second; a survivor of sepsis or heavy bleeding has to eat well."
    )
    .defineInRange("fedBloodRegenPerTick", 0.05, 0.0, 100.0, classOf[Double])
  val MaxImmuneHealth: ConfigValue[Double] = Builder
    .comment(
      "Maximum (and starting) immune health. With the default infection rates, the break-even",
      "point where the immune system exactly matches infection spread is 120 out of 200."
    )
    .gameRestart()
    .defineInRange("maxImmuneHealth", 200.0, 1.0, 10000.0, classOf[Double])
  Builder.pop()

  Builder.push("hazards")
  val TerminalHypoxiaDurationTicks: ConfigValue[Integer] = Builder
    .comment(
      "Ticks spent at zero blood oxygen while respiration remains failed before terminal hypoxia",
      "deals its fatal hit (20 ticks = 1 second). Must remain positive; ending the vanilla",
      "breathing block or opioid respiratory failure resets the persisted exposure timer."
    )
    .defineInRange("terminalHypoxiaDurationTicks", 160, 1, 72000)
  val InWallBloodOxygenDepletionPerTick: ConfigValue[Double] = Builder
    .comment(
      "Blood oxygen lost per tick while the player's head is in a wall. When exhausted-air",
      "drowning is active simultaneously, only the stronger of this and",
      "vitals.bloodOxygenDepletionPerTick applies."
    )
    .defineInRange("inWallBloodOxygenDepletionPerTick", 0.6, 0.0, 100.0, classOf[Double])
  val StarvationBloodLossFractionPerDamage: ConfigValue[Double] = Builder
    .comment(
      "Fraction of healthy max blood lost per accepted vanilla starvation damage point.",
      "The default 0.05 drains 250 mL per normal 1.0-damage pulse at 5000 mL healthy capacity."
    )
    .defineInRange("starvationBloodLossFractionPerDamage", 0.05, 0.0, 1.0, classOf[Double])
  val EasyStarvationBloodFloorFraction: ConfigValue[Double] = Builder
    .comment(
      "Easy starvation floor as a fraction of effective post-sepsis max blood. Vanilla hurt",
      "pulses stop at the floor; the default 0.5 leaves half of effective blood."
    )
    .defineInRange("easyStarvationBloodFloorFraction", 0.5, 0.0, 1.0, classOf[Double])
  val NormalStarvationBloodFloorFraction: ConfigValue[Double] = Builder
    .comment(
      "Normal starvation floor as a fraction of effective post-sepsis max blood. It is clamped",
      "no higher than the Easy floor at use time; the default 0.05 leaves five percent."
    )
    .defineInRange("normalStarvationBloodFloorFraction", 0.05, 0.0, 1.0, classOf[Double])
  Builder.pop()

  Builder.push("limbs")
  val StartingMuscleHealth: ConfigValue[Double] = Builder
    .comment("Muscle health each limb starts with.")
    .gameRestart()
    .defineInRange("startingMuscleHealth", 100.0, 0.0, 100.0, classOf[Double])
  val StartingSkinIntegrity: ConfigValue[Double] = Builder
    .comment("Skin integrity each limb starts with.")
    .gameRestart()
    .defineInRange("startingSkinIntegrity", 100.0, 0.0, 100.0, classOf[Double])
  Builder.pop()

  Builder.push("bleeding")
  val ClottingRatePerTick: ConfigValue[Double] = Builder
    .comment(
      "External bleeding clots linearly: this many mL/tick of bleeding rate are sealed per tick.",
      "At the default, a fresh sword cut (0.2 mL/tick) clots shut in 4 minutes 10 seconds;",
      "a limb at the maximum 1.0 mL/tick takes 20 minutes 50 seconds."
    )
    .defineInRange("clottingRatePerTick", 0.00004, 0.0, 1.0, classOf[Double])
  val MaxExternalBleedingRate: ConfigValue[Double] = Builder
    .comment(
      "Upper bound of a limb's external bleeding rate (mL/tick) when its skin integrity is zero.",
      "The bound scales linearly with skin integrity: intact skin cannot bleed, half-intact skin",
      "bleeds at most half this rate."
    )
    .defineInRange("maxExternalBleedingRate", 1.0, 0.0, 100.0, classOf[Double])
  val BleedingRateJitter: ConfigValue[Double] = Builder
    .comment(
      "Random fluctuation of the bleeding rate granted by each wound, as a fraction of the rate",
      "(0.3 = rolled as rate × (1 ± 30%)); proportional, so larger wounds fluctuate more.",
      "0 disables fluctuation."
    )
    .defineInRange("bleedingRateJitter", 0.3, 0.0, 1.0, classOf[Double])
  val TotemBloodRestoreFraction: ConfigValue[Double] = Builder
    .comment(
      "Fraction of the effective maximum blood volume restored when death protection saves",
      "a player from blood loss. It must stay positive to avoid a zero-blood rescue loop; wounds",
      "remain open and keep bleeding."
    )
    .defineInRange("totemBloodRestoreFraction", 0.2, 0.000001, 1.0, classOf[Double])
  val TotemHemostasisInitialReduction: ConfigValue[Double] = Builder
    .comment(
      "Initial fraction of actual blood drain prevented after a blood-loss totem rescue.",
      "The reduction decays linearly to zero over totemHemostasisDurationTicks."
    )
    .defineInRange("totemHemostasisInitialReduction", 0.8, 0.0, 1.0, classOf[Double])
  val TotemHemostasisDurationTicks: ConfigValue[Integer] = Builder
    .comment(
      "Duration of the post-totem hemostasis window in ticks (20 ticks = 1 second).",
      "The hidden timer freezes with the rest of physiology in creative and spectator modes."
    )
    .defineInRange("totemHemostasisDurationTicks", 600, 0, 72000)
  Builder.pop()

  Builder.push("infection")
  val InfectionChancePerTick: ConfigValue[Double] = Builder
    .comment(
      "Per-tick probability that a wound starts an infection, at zero skin integrity; scales",
      "linearly with the skin damage (half-intact skin: half the chance). Only wounds below",
      "the skin integrity threshold (20 damage, i.e. under 80) can get infected at all.",
      "0 disables infections."
    )
    .defineInRange("infectionChancePerTick", 0.0005, 0.0, 1.0, classOf[Double])
  val InfectionSpreadPerTick: ConfigValue[Double] = Builder
    .comment(
      "Infection progress gained per tick at zero immune health; scales down linearly and",
      "reaches zero at full immune health. 0.03 = an unchecked infection runs 0 to 100 in",
      "~2.8 min."
    )
    .defineInRange("infectionSpreadPerTick", 0.03, 0.0, 10.0, classOf[Double])
  val InfectionFightPerTick: ConfigValue[Double] = Builder
    .comment(
      "Infection progress removed per tick at full immune health; scales down linearly and",
      "reaches zero at zero immune health. With the defaults (spread 0.03, fight 0.02, max",
      "immune health 200), the break-even immune health is 120: above it infections recede,",
      "below it they spread."
    )
    .defineInRange("infectionFightPerTick", 0.02, 0.0, 10.0, classOf[Double])
  val SkinRegenMinImmuneMultiplier: ConfigValue[Double] = Builder
    .comment(
      "Skin regrowth multiplier at zero immune health, as a fraction of the base rate (0.25 =",
      "quarter speed); at full immune health skin regrows at the base rate. Never zero, so a",
      "dying player is not soft-locked out of healing."
    )
    .defineInRange("skinRegenMinImmuneMultiplier", 0.25, 0.0, 1.0, classOf[Double])
  val InfectionEffectStartProgress: ConfigValue[Double] = Builder
    .comment(
      "Infection progress at which local consequences (pain, muscle decay) begin; below this",
      "an infection is asymptomatic."
    )
    .defineInRange("infectionEffectStartProgress", 20.0, 0.0, 100.0, classOf[Double])
  val InfectionEffectFullProgress: ConfigValue[Double] = Builder
    .comment(
      "Infection progress at which local consequences reach full strength; between the start",
      "and this value the effect strength ramps up linearly."
    )
    .defineInRange("infectionEffectFullProgress", 40.0, 0.0, 100.0, classOf[Double])
  val InfectionPainPerTick: ConfigValue[Double] = Builder
    .comment(
      "Pain granted per tick by an infection at full effect strength (see",
      "infectionEffectFullProgress). At the default, a full-strength infection outruns natural",
      "pain decay, so the limb keeps hurting until the infection recedes."
    )
    .defineInRange("infectionPainPerTick", 0.05, 0.0, 10.0, classOf[Double])
  val InfectionMuscleDecayPerTick: ConfigValue[Double] = Builder
    .comment(
      "Muscle health destroyed per tick by an infection at full effect strength (see",
      "infectionEffectFullProgress). At the default (double the muscle regrowth rate), a",
      "full-strength infection wastes the limb away in about half an hour."
    )
    .defineInRange("infectionMuscleDecayPerTick", 0.005, 0.0, 10.0, classOf[Double])
  val InfectionContagionStartProgress: ConfigValue[Double] = Builder
    .comment(
      "Infection progress at which a limb can start seeding infections into anatomically",
      "adjacent limbs; below this it never spreads."
    )
    .defineInRange("infectionContagionStartProgress", 60.0, 0.0, 100.0, classOf[Double])
  val InfectionContagionFullProgress: ConfigValue[Double] = Builder
    .comment(
      "Infection progress at which the contagion chance reaches its maximum; between the",
      "start and this value the per-tick chance ramps up linearly."
    )
    .defineInRange("infectionContagionFullProgress", 80.0, 0.0, 100.0, classOf[Double])
  val InfectionContagionMaxChancePerTick: ConfigValue[Double] = Builder
    .comment(
      "Per-tick probability of seeding a random uninfected adjacent limb at full ramp",
      "(0.05 = on average one spread per second); the seeded limb starts with a small",
      "infection progress, like a fresh wound onset."
    )
    .defineInRange("infectionContagionMaxChancePerTick", 0.05, 0.0, 1.0, classOf[Double])
  Builder.pop()

  Builder.push("sepsis")
  val MaxSepsis: ConfigValue[Double] = Builder
    .comment(
      "The maximum sepsis value; reaching it reduces the effective blood volume cap to zero."
    )
    .defineInRange("maxSepsis", 100.0, 1.0, 10000.0, classOf[Double])
  val SepsisGainPerTick: ConfigValue[Double] = Builder
    .comment(
      "Sepsis gained per tick when the whole body is maximally infected (infection load 600),",
      "scaled linearly with the load fraction."
    )
    .defineInRange("sepsisGainPerTick", 0.05, 0.0, 10.0, classOf[Double])
  val SepsisDecayPerTick: ConfigValue[Double] = Builder
    .comment(
      "Sepsis recovered per tick regardless of the infection load. With the defaults the",
      "break-even infection load is 360 out of 600: a single maxed-out limb infection cannot",
      "kill, but an infection spreading across the body does."
    )
    .defineInRange("sepsisDecayPerTick", 0.03, 0.0, 10.0, classOf[Double])
  Builder.pop()

  Builder.push("armor")
  val ArmorSkinFactorFormula: FormulaConfigValue = new FormulaConfigValue(
    Builder,
    "skinFactorFormula",
    "max(0.05, min(1, 1 - armor * 0.1 - toughness * 0.02))",
    List("armor", "toughness"),
    comment = Seq(
      "Skin damage multiplier from the piece covering the struck body part, compiled with",
      "EvalEx (https://github.com/ezylang/EvalEx). Available variables: armor (armor points),",
      "toughness (armor toughness points). Skin and bleeding coefficients of the wound profile",
      "are multiplied by this factor. Invalid formulas are rejected and corrected to the default.",
      "Hot-reloaded on file change."
    )
  )
  val ArmorMuscleFactorFormula: FormulaConfigValue = new FormulaConfigValue(
    Builder,
    "muscleFactorFormula",
    "1 - (1 - skinFactor) * 0.5",
    List("armor", "toughness", "skinFactor"),
    comment = Seq(
      "Muscle damage multiplier from the covering armor piece: the blunt impact that still",
      "transmits through the armor. Available variables: armor, toughness, skinFactor (the",
      "evaluated skinFactorFormula result). Muscle and pain coefficients of the wound profile",
      "are multiplied by this factor."
    )
  )
  Builder.pop()

  // Discomfort: how revolting a food is. Which food is how revolting is content — datapack
  // tier tags (casualtiesbelow:discomfort_1/2/3) plus per-item datapack overrides
  // (data/*/casualtiesbelow/discomfort/*.json); this section prices the tiers, the float, the
  // decay, the state multipliers and the consequence thresholds.
  Builder.push("discomfort")
  val MaxDiscomfort: ConfigValue[Double] = Builder
    .comment("Maximum discomfort value.")
    .defineInRange("maxValue", 100.0, 1.0, 10000.0, classOf[Double])
  val DiscomfortDistribution: ModConfigSpec.EnumValue[Distribution] = Builder
    .comment(
      "Sampling distribution for each bite.",
      "Gaussian: normal distribution with possible tails.",
      "Uniform: even distribution across the configured interval."
    )
    .defineEnum("distribution", Distribution.Gaussian)
  val DiscomfortSpreadFraction: ConfigValue[Double] = Builder
    .comment(
      "Spread of one dose as a fraction of its tier mean (gaussian standard deviation or",
      "uniform half-width), so every tier wobbles proportionally."
    )
    .defineInRange("spreadFraction", 0.25, 0.0, 1.0, classOf[Double])
  val DiscomfortLevel1Mean: ConfigValue[Double] = Builder
    .comment(
      "Mean discomfort of tier-1 food (the casualtiesbelow:discomfort_1 tag: raw fish, honey",
      "by the bottle): a snackable nuisance — four or five in a row start to matter."
    )
    .defineInRange("level1Mean", 7.0, 0.0, 10000.0, classOf[Double])
  val DiscomfortLevel2Mean: ConfigValue[Double] = Builder
    .comment(
      "Mean discomfort of tier-2 food (the casualtiesbelow:discomfort_2 tag: raw meat, dried",
      "kelp, chorus fruit): two start nausea, four hit refusal."
    )
    .defineInRange("level2Mean", 15.0, 0.0, 10000.0, classOf[Double])
  val DiscomfortLevel3Mean: ConfigValue[Double] = Builder
    .comment(
      "Mean discomfort of tier-3 food (the casualtiesbelow:discomfort_3 tag: rotten,",
      "poisonous, not-human-food): one is felt, three induce vomiting."
    )
    .defineInRange("level3Mean", 30.0, 0.0, 10000.0, classOf[Double])
  val DiscomfortNauseaThreshold: ConfigValue[Double] = Builder
    .comment("Discomfort at or above which the nausea screen effect is kept up.")
    .defineInRange("nauseaThreshold", 30.0, 0.0, 10000.0, classOf[Double])
  val DiscomfortMaxVignetteOpacity: ConfigValue[Double] = Builder
    .comment(
      "Strongest nausea edge-darkening strength (0.0-1.0), reached at maximum discomfort.",
      "This effect does not add low-consciousness blur or pulsing; 0 disables it."
    )
    .defineInRange("maxVignetteOpacity", 0.35, 0.0, 1.0, classOf[Double])
  val DiscomfortRefusalThreshold: ConfigValue[Double] = Builder
    .comment(
      "Discomfort at or above which discomfort-bearing food can no longer be started —",
      "the character cannot bring themselves to swallow it."
    )
    .defineInRange("refusalThreshold", 60.0, 0.0, 10000.0, classOf[Double])
  val DiscomfortVomitChanceThreshold: ConfigValue[Double] = Builder
    .comment(
      "Discomfort above which each server tick can trigger vomiting; the chance rises",
      "linearly from vomitMinChancePerTick here to vomitMaxChancePerTick at maxValue."
    )
    .defineInRange("vomitChanceThreshold", 30.0, 0.0, 10000.0, classOf[Double])
  val DiscomfortVomitMinChancePerTick: ConfigValue[Double] = Builder
    .comment("Vomiting chance per tick immediately above vomitChanceThreshold (0.01 = 1%).")
    .defineInRange("vomitMinChancePerTick", 0.01, 0.0, 1.0, classOf[Double])
  val DiscomfortVomitMaxChancePerTick: ConfigValue[Double] = Builder
    .comment("Vomiting chance per tick at maxValue (0.05 = 5%).")
    .defineInRange("vomitMaxChancePerTick", 0.05, 0.0, 1.0, classOf[Double])
  val DiscomfortDecayLowPerSecond: ConfigValue[Double] = Builder
    .comment(
      "Discomfort decay per second while below the nausea threshold: mild queasiness is",
      "tough to notice and fades on its own."
    )
    .defineInRange("decayRateLowPerSecond", 0.5, 0.0, 1000.0, classOf[Double])
  val DiscomfortDecayHighPerSecond: ConfigValue[Double] = Builder
    .comment(
      "Discomfort decay per second while at or above the nausea threshold: real sickness",
      "lingers and asks for active resolution."
    )
    .defineInRange("decayRateHighPerSecond", 0.2, 0.0, 1000.0, classOf[Double])
  val DiscomfortNauseousMultiplier: ConfigValue[Double] = Builder
    .comment("Dose multiplier when eating while already at or above the nausea threshold.")
    .defineInRange("alreadyNauseousMultiplier", 1.25, 0.0, 100.0, classOf[Double])
  val DiscomfortOvereatingMultiplier: ConfigValue[Double] = Builder
    .comment("Dose multiplier when force-feeding on a full stomach (always-edible foods).")
    .defineInRange("overeatingMultiplier", 1.25, 0.0, 100.0, classOf[Double])
  val DiscomfortPoorConditionMultiplier: ConfigValue[Double] = Builder
    .comment(
      "Dose multiplier when eating while septic or barely conscious (below",
      "poorConditionConsciousnessThreshold)."
    )
    .defineInRange("poorConditionMultiplier", 1.5, 0.0, 100.0, classOf[Double])
  val DiscomfortPoorConditionConsciousness: ConfigValue[Double] = Builder
    .comment("Consciousness below which the poor-condition dose multiplier applies.")
    .defineInRange("poorConditionConsciousnessThreshold", 50.0, 0.0, 100.0, classOf[Double])
  val DiscomfortVomitHungerPenalty: ConfigValue[Integer] = Builder
    .comment("Food level (0-20) lost when vomiting.")
    .defineInRange("vomitHungerPenalty", 6, 0, 20, classOf[Integer])
  val DiscomfortVomitSaturationPenalty: ConfigValue[Double] = Builder
    .comment("Saturation lost when vomiting.")
    .defineInRange("vomitSaturationPenalty", 8.0, 0.0, 100.0, classOf[Double])
  val DiscomfortVomitRelief: ConfigValue[Double] = Builder
    .comment("Mean discomfort removed by vomiting.")
    .defineInRange("vomitRelief", 30.0, 0.0, 10000.0, classOf[Double])
  val DiscomfortVomitReliefSpreadFraction: ConfigValue[Double] = Builder
    .comment(
      "Uniform random spread around vomitRelief as a fraction of that value",
      "(0.05 = each vomit removes between 95% and 105% of the configured relief)."
    )
    .defineInRange("vomitReliefSpreadFraction", 0.05, 0.0, 1.0, classOf[Double])
  Builder.pop()

  Builder.push("immune")
  val FedImmuneRegenPerTick: ConfigValue[Double] = Builder
    .comment(
      "Immune health regained per tick while awake and fed (food level at or above",
      "fedFoodLevelThreshold)."
    )
    .defineInRange("fedImmuneRegenPerTick", 0.005, 0.0, 10.0, classOf[Double])
  val HungryImmuneDrainPerTick: ConfigValue[Double] = Builder
    .comment(
      "Immune health lost per tick while hungry (food level below hungryFoodLevelThreshold)."
    )
    .defineInRange("hungryImmuneDrainPerTick", 0.01, 0.0, 10.0, classOf[Double])
  val FedFoodLevelThreshold: ConfigValue[Integer] = Builder
    .comment(
      "Food level (0-20) at or above which immune health regenerates while awake; 18 matches",
      "vanilla's natural-regeneration threshold."
    )
    .defineInRange("fedFoodLevelThreshold", 18, 0, 20, classOf[Integer])
  val HungryFoodLevelThreshold: ConfigValue[Integer] = Builder
    .comment(
      "Food level (0-20) below which immune health drains; 7 matches vanilla's sprinting",
      "cutoff (vanilla requires food > 6 to sprint, so at 6 the player is already exhausted)."
    )
    .defineInRange("hungryFoodLevelThreshold", 7, 0, 20, classOf[Integer])
  val ZombieHitImmuneDrain: ConfigValue[Double] = Builder
    .comment(
      "Immune health lost per zombie-family hit (entity type tag minecraft:zombies), rolled",
      "with zombieHitImmuneDrainJitter fluctuation. One-way feedback: only external attacks",
      "drain immune health; infections never do."
    )
    .defineInRange("zombieHitImmuneDrain", 5.0, 0.0, 1000.0, classOf[Double])
  val ZombieHitImmuneDrainJitter: ConfigValue[Double] = Builder
    .comment(
      "Random fluctuation of the per-hit immune drain, as a fraction of the drain (0.3 =",
      "rolled as drain × (1 ± 30%)); proportional, so a larger drain fluctuates more. 0",
      "disables fluctuation."
    )
    .defineInRange("zombieHitImmuneDrainJitter", 0.3, 0.0, 1.0, classOf[Double])
  val PoisonImmuneDrainPerTick: ConfigValue[Double] = Builder
    .comment(
      "Immune health lost per tick while vanilla Poison is active, before linear effect-level",
      "scaling (Poison I = 1×, Poison II = 2×). This combines additively with diet; 0",
      "disables poison immune drain."
    )
    .defineInRange("poisonImmuneDrainPerTick", 0.05, 0.0, 10.0, classOf[Double])
  val FoodImmuneSpreadFraction: ConfigValue[Double] = Builder
    .comment(
      "Spread of one food immune dose as a fraction of its absolute mean (gaussian standard",
      "deviation), so healthy and contaminated foods wobble proportionally; the sign of the",
      "mean is always preserved. 0 disables fluctuation."
    )
    .defineInRange("foodImmuneSpreadFraction", 0.2, 0.0, 1.0, classOf[Double])
  Builder.pop()

  Builder.push("dirtiness")
  val MaxDirtiness: ConfigValue[Double] = Builder
    .comment(
      "Maximum dirtiness value. Dirtiness is a whole-body hygiene axis (0 = clean): the",
      "environment and the player's own actions push it up, only water washes it down.",
      "Display bands (grimy/filthy/squalid) only drive the screen presentation; every",
      "mechanic computes from the raw value."
    )
    .defineInRange("maxValue", 100.0, 1.0, 10000.0, classOf[Double])
  val DirtinessAccrualPerSecond: ConfigValue[Double] = Builder
    .comment(
      "Passive dirtiness accrual per second. At the default, total neglect reaches the",
      "filthy display band (60) in ~33 minutes of play (about 1.7 in-game days)."
    )
    .defineInRange("accrualPerSecond", 0.03, 0.0, 1000.0, classOf[Double])
  val DirtinessSprintMultiplier: ConfigValue[Double] = Builder
    .comment("Accrual multiplier while sprinting (sweat). Multiplicative with the others.")
    .defineInRange("sprintMultiplier", 2.5, 0.0, 100.0, classOf[Double])
  val DirtinessArmoredMultiplier: ConfigValue[Double] = Builder
    .comment("Accrual multiplier while wearing a full armor set (heat buildup).")
    .defineInRange("armoredMultiplier", 1.3, 0.0, 100.0, classOf[Double])
  val DirtinessNetherMultiplier: ConfigValue[Double] = Builder
    .comment(
      "Accrual multiplier in Nether biomes (biome tag minecraft:is_nether): ash and heat,",
      "combining with vanilla's no-water rule to create hygiene pressure."
    )
    .defineInRange("netherMultiplier", 1.5, 0.0, 100.0, classOf[Double])
  val DirtinessWashWaterPerSecond: ConfigValue[Double] = Builder
    .comment(
      "Dirtiness removed per second while immersed in water. At the default a fully dirty",
      "player is clean after ~21 seconds; washing always far outruns accrual."
    )
    .defineInRange("washWaterPerSecond", 4.8, 0.0, 1000.0, classOf[Double])
  val DirtinessWashRainPerSecond: ConfigValue[Double] = Builder
    .comment("Dirtiness removed per second while exposed to rain: free but slow.")
    .defineInRange("washRainPerSecond", 0.6, 0.0, 1000.0, classOf[Double])
  val DirtinessCauldronPointsPerLevel: ConfigValue[Double] = Builder
    .comment(
      "Dirtiness washed per consumed water level while standing in a water cauldron. At",
      "the default one full cauldron (3 levels = 102 points) covers a full 0-100 wash.",
      "Cauldrons are the only legal water storage in the Nether."
    )
    .defineInRange("cauldronPointsPerLevel", 34.0, 0.0, 10000.0, classOf[Double])
  val DirtyWaterWashMultiplier: ConfigValue[Double] = Builder
    .comment(
      "Wash-rate multiplier in murky water (biome tag casualtiesbelow:dirty_water, e.g.",
      "swamps): 0.5 = half as effective. 1.0 disables the distinction."
    )
    .defineInRange("dirtyWaterWashMultiplier", 0.5, 0.0, 1.0, classOf[Double])
  val DirtinessZombieHit: ConfigValue[Double] = Builder
    .comment(
      "Dirtiness pulse per zombie-family hit received, rolled with pulseJitter. Same",
      "contact-grime theme as zombieHitImmuneDrain."
    )
    .defineInRange("zombieHitDirt", 3.0, 0.0, 1000.0, classOf[Double])
  val DirtinessMobHit: ConfigValue[Double] = Builder
    .comment("Dirtiness pulse per other monster hit received, rolled with pulseJitter.")
    .defineInRange("mobHitDirt", 1.0, 0.0, 1000.0, classOf[Double])
  val DirtinessExplosion: ConfigValue[Double] = Builder
    .comment("Dirtiness pulse when caught in an explosion: soot and debris.")
    .defineInRange("explosionDirt", 5.0, 0.0, 1000.0, classOf[Double])
  val DirtinessMeleeKill: ConfigValue[Double] = Builder
    .comment("Dirtiness pulse per melee kill: blood and spatter.")
    .defineInRange("meleeKillDirt", 0.8, 0.0, 1000.0, classOf[Double])
  val DirtinessDigDirtyBlock: ConfigValue[Double] = Builder
    .comment(
      "Dirtiness pulse per broken loose block (block tag casualtiesbelow:dirty_diggable:",
      "dirt, sand, gravel and the like)."
    )
    .defineInRange("digDirtyBlockDirt", 0.04, 0.0, 1000.0, classOf[Double])
  val DirtinessDigBasicBlock: ConfigValue[Double] = Builder
    .comment(
      "Dirtiness pulse per broken block in neither diggability tag (stone, ore and the",
      "like): the default digging tier, half of loose."
    )
    .defineInRange("digBasicBlockDirt", 0.02, 0.0, 1000.0, classOf[Double])
  val DirtinessDigDustlessBlock: ConfigValue[Double] = Builder
    .comment(
      "Dirtiness pulse per broken dustless block (block tag",
      "casualtiesbelow:dustless_diggable: leaves, wool, wood, glass): 0 skips the pulse",
      "entirely, no jitter roll."
    )
    .defineInRange("digDustlessBlockDirt", 0.0, 0.0, 1000.0, classOf[Double])
  val DirtinessFoodFraction: ConfigValue[Double] = Builder
    .comment(
      "Dirtiness pulse of a eaten food as a fraction of its discomfort tier mean (0.1 =",
      "rotten flesh adds 3, raw meat 1.5): the hygiene risk of contaminated food."
    )
    .defineInRange("foodDirtFraction", 0.1, 0.0, 10.0, classOf[Double])
  val DirtinessHusbandry: ConfigValue[Double] = Builder
    .comment("Dirtiness pulse per animal-husbandry interaction (shearing, milking).")
    .defineInRange("husbandryDirt", 0.5, 0.0, 1000.0, classOf[Double])
  val DirtinessPulseJitter: ConfigValue[Double] = Builder
    .comment(
      "Random fluctuation of every dirtiness pulse, as a fraction of the pulse (0.3 =",
      "rolled as pulse × (1 ± 30%)); proportional, so a larger pulse fluctuates more."
    )
    .defineInRange("pulseJitter", 0.3, 0.0, 1.0, classOf[Double])
  val DirtinessInfectionChanceMultiplierAtMax: ConfigValue[Double] = Builder
    .comment(
      "Additional wound-infection chance multiplier at maximum dirtiness: the per-tick",
      "infection chance scales with (1 + value × dirtiness/maxValue). 2.0 = triple",
      "chance when fully dirty."
    )
    .defineInRange("infectionChanceMultiplierAtMax", 2.0, 0.0, 100.0, classOf[Double])
  val DirtinessInjectionSeedAtMax: ConfigValue[Double] = Builder
    .comment(
      "Infection progress seeded into the injected limb by one full syringe at maximum",
      "dirtiness; scales linearly with dirtiness and with the pushed fraction. The",
      "default stays below the symptomatic threshold (infectionEffectStartProgress 20):",
      "a dirty needle alone cannot cause symptomatic infection."
    )
    .defineInRange("injectionSeedAtMax", 12.0, 0.0, 100.0, classOf[Double])
  val DirtinessSkinRegenMinMultiplier: ConfigValue[Double] = Builder
    .comment(
      "Skin-regrowth multiplier at maximum dirtiness, ramping linearly from 1 when",
      "clean. Never zero, and stacks with the immune multiplier."
    )
    .defineInRange("skinRegenMinMultiplier", 0.75, 0.0, 1.0, classOf[Double])
  val DirtinessFoodDiscomfortMultiplierAtMax: ConfigValue[Double] = Builder
    .comment(
      "Additional food-discomfort dose fraction at maximum dirtiness (eating with dirty",
      "hands): the dose scales with (1 + value × dirtiness/maxValue)."
    )
    .defineInRange("foodDiscomfortMultiplierAtMax", 0.5, 0.0, 100.0, classOf[Double])
  val DirtinessImmuneDrainStart: ConfigValue[Double] = Builder
    .comment(
      "Dirtiness at which the continuous immune drain starts; the drain ramps linearly",
      "to immuneDrainMaxPerTick at maxValue."
    )
    .defineInRange("immuneDrainStartDirtiness", 50.0, 0.0, 10000.0, classOf[Double])
  val DirtinessImmuneDrainMaxPerTick: ConfigValue[Double] = Builder
    .comment(
      "Immune health drained per tick at maximum dirtiness. The default equals",
      "hungryImmuneDrainPerTick (maximally dirty = starving): a full 200 immune drains",
      "in ~16.7 minutes, and at 75 dirtiness the drain exactly offsets",
      "fedImmuneRegenPerTick."
    )
    .defineInRange("immuneDrainMaxPerTick", 0.01, 0.0, 10.0, classOf[Double])
  val DirtinessBandGrimy: ConfigValue[Double] = Builder
    .comment(
      "Dirtiness of the grimy display band: the grime vignette starts appearing here.",
      "Display only; no mechanic reads the bands."
    )
    .defineInRange("bandGrimy", 30.0, 0.0, 10000.0, classOf[Double])
  val DirtinessBandFilthy: ConfigValue[Double] = Builder
    .comment("Dirtiness of the filthy display band. Display only.")
    .defineInRange("bandFilthy", 60.0, 0.0, 10000.0, classOf[Double])
  val DirtinessBandSqualid: ConfigValue[Double] = Builder
    .comment("Dirtiness of the squalid display band. Display only.")
    .defineInRange("bandSqualid", 85.0, 0.0, 10000.0, classOf[Double])
  val DirtinessGrimeVignetteMaxOpacity: ConfigValue[Double] = Builder
    .comment(
      "Strongest grime vignette opacity (0.0-1.0), ramping from bandGrimy to maxValue;",
      "brown-toned, distinct from the nausea green. 0 disables it."
    )
    .defineInRange("grimeVignetteMaxOpacity", 0.55, 0.0, 1.0, classOf[Double])
  Builder.pop()

  Builder.push("regeneration")
  val RegenerationSkinRestorePerTick: ConfigValue[Double] = Builder
    .comment(
      "Skin integrity restored per tick on every damaged limb while vanilla Regeneration is",
      "active, before linear effect-level scaling (Regeneration I = 1×, Regeneration II = 2×).",
      "Recovery continues while bleeding and immediately tightens the bleeding cap; 0 disables it."
    )
    .defineInRange("skinRestorePerTick", 0.001, 0.0, 10.0, classOf[Double])
  Builder.pop()

  Builder.push("movement")
  val DislocationSpeedReduction: ConfigValue[Double] = Builder
    .comment(
      "Movement speed multiplier reduction per dislocated leg, as a fraction (e.g. 0.3 = 30% slower).",
      "A fractured leg counts as 1.5 dislocated legs."
    )
    .gameRestart()
    .defineInRange("dislocationSpeedReduction", 0.3, 0.0, 1.0, classOf[Double])
  val DislocationJumpReduction: ConfigValue[Double] = Builder
    .comment(
      "Jump strength multiplier reduction per dislocated leg, as a fraction (e.g. 0.5 = 50% lower jumps).",
      "A fractured leg counts as 1.5 dislocated legs."
    )
    .gameRestart()
    .defineInRange("dislocationJumpReduction", 0.2, 0.0, 1.0, classOf[Double])
  val MuscleSpeedReduction: ConfigValue[Double] = Builder
    .comment(
      "Maximum movement speed multiplier reduction from leg muscle damage, reached when both",
      "legs have zero muscle health. Each leg's normalized deficit is squared, then both legs",
      "are averaged; this layer multiplies independently with fracture and dislocation penalties."
    )
    .gameRestart()
    .defineInRange("muscleSpeedReduction", 0.75, 0.0, 1.0, classOf[Double])
  val MuscleJumpReduction: ConfigValue[Double] = Builder
    .comment(
      "Maximum jump strength multiplier reduction from leg muscle damage, reached when both",
      "legs have zero muscle health. Uses the same squared-deficit average as movement speed."
    )
    .gameRestart()
    .defineInRange("muscleJumpReduction", 0.6, 0.0, 1.0, classOf[Double])
  Builder.pop()

  Builder.push("pain")
  val PainStrategy: ModConfigSpec.EnumValue[TotalPainStrategy] = Builder
    .comment(
      "How per-limb pains are aggregated into whole-body pain.",
      "Max: only the worst injury counts. Sum: all pains add up.",
      "Geometric: descending-sorted pains weighted d^0, d^1, d^2, ... (see totalPainDecay)."
    )
    .defineEnum("totalPainStrategy", TotalPainStrategy.Geometric)
  val TotalPainDecay: ConfigValue[Double] = Builder
    .comment(
      "Geometric strategy decay factor: limb pains sorted descending are weighted d^0, d^1, d^2, ...",
      "Lower values mean additional injuries beyond the worst count less."
    )
    .defineInRange("totalPainDecay", 0.3, 0.0, 1.0, classOf[Double])
  val TotalPainFilterThreshold: ConfigValue[Double] = Builder
    .comment(
      "Geometric strategy: limb pains below this value do not contribute (0 = no filtering).",
      "If every pain is filtered out, the worst single pain still counts."
    )
    .defineInRange("totalPainFilterThreshold", 0.0, 0.0, 100.0, classOf[Double])
  val PainDecayPerTick: ConfigValue[Double] = Builder
    .comment("Pain faded per tick on every limb (0.025 = a full limb's pain fades in ~200 s).")
    .defineInRange("painDecayPerTick", 0.025, 0.0, 10.0, classOf[Double])
  val ShockAccumulationStartPain: ConfigValue[Double] = Builder
    .comment(
      "Whole-body pain above which hidden pain-shock load starts accumulating.",
      "At or below this value the load instead recovers at shockRecoveryPerTick."
    )
    .defineInRange("shockAccumulationStartPain", 70.0, 0.0, 100.0, classOf[Double])
  val ShockMaximumRatePain: ConfigValue[Double] = Builder
    .comment(
      "Whole-body pain at which shock load reaches its maximum accumulation rate.",
      "Between the start and maximum-rate thresholds the rate scales linearly; an effective",
      "value below shockAccumulationStartPain is treated as equal to it."
    )
    .defineInRange("shockMaximumRatePain", 80.0, 0.0, 100.0, classOf[Double])
  val ShockMaximumGainPerTick: ConfigValue[Double] = Builder
    .comment(
      "Maximum hidden shock load gained per tick at or above shockMaximumRatePain.",
      "The default 0.2 is 4 load per second, so 0 to the default collapse threshold takes 22.5 s."
    )
    .defineInRange("shockMaximumGainPerTick", 0.2, 0.0, 100.0, classOf[Double])
  val ShockRecoveryPerTick: ConfigValue[Double] = Builder
    .comment(
      "Shock load removed per tick while whole-body pain is at or below the accumulation start.",
      "The default 0.1 removes 2 load per second."
    )
    .defineInRange("shockRecoveryPerTick", 0.1, 0.0, 100.0, classOf[Double])
  val ShockCollapseThreshold: ConfigValue[Double] = Builder
    .comment(
      "Shock load that collapses a stable player when crossed upward and permits consciousness",
      "recovery when crossed downward. Load can continue accumulating to 100 after collapse."
    )
    .defineInRange(
      "shockCollapseThreshold",
      90.0,
      VitalsComponent.MinimumWakeThreshold,
      VitalsComponent.MaxValue,
      classOf[Double]
    )
  val ShockWakeLoadCap: ConfigValue[Double] = Builder
    .comment(
      "Maximum hidden shock load retained when a recovering player wakes.",
      "The default zero fully clears residual load, giving every new episode the full 22.5-second",
      "accumulation window at the maximum gain rate."
    )
    .defineInRange("shockWakeLoadCap", 0.0, 0.0, 100.0, classOf[Double])
  val ShockVisualStartLoad: ConfigValue[Double] = Builder
    .comment(
      "Client-local shock load at which the warm peripheral warning starts fading in.",
      "This display value is not synchronized from dedicated servers."
    )
    .defineInRange("shockVisualStartLoad", 0.0, 0.0, 100.0, classOf[Double])
  val ShockVisualMaxStrength: ConfigValue[Double] = Builder
    .comment(
      "Client-local maximum warm peripheral warning strength (0.0-1.0).",
      "Set to zero to disable the pain-shock warning without changing gameplay."
    )
    .defineInRange("shockVisualMaxStrength", 1.0, 0.0, 1.0, classOf[Double])
  val ShockVisualPulseStrength: ConfigValue[Double] = Builder
    .comment(
      "Client-local depth of the slow warning pulse during the final half of the load ramp.",
      "Set to zero for a static peripheral warning."
    )
    .defineInRange("shockVisualPulseStrength", 0.2, 0.0, 1.0, classOf[Double])
  val ShockVisualNoiseStrength: ConfigValue[Double] = Builder
    .comment(
      "Client-local brightness variation of the animated static at the warning edge.",
      "The default is clearly visible but remains peripheral; zero disables it independently."
    )
    .defineInRange("shockVisualNoiseStrength", 0.25, 0.0, 0.5, classOf[Double])
  val FracturedWalkingPainPerTick: ConfigValue[Double] = Builder
    .comment(
      "Pain granted per tick while walking on a fractured leg, at full tissue damage (muscle and",
      "skin both at zero); scales linearly with the leg's average tissue damage."
    )
    .defineInRange("fracturedWalkingPainPerTick", 0.5, 0.0, 10.0, classOf[Double])
  val DislocatedWalkingPainPerTick: ConfigValue[Double] = Builder
    .comment(
      "Same as fracturedWalkingPainPerTick, but for walking on a dislocated leg."
    )
    .defineInRange("dislocatedWalkingPainPerTick", 0.3, 0.0, 10.0, classOf[Double])
  Builder.pop()

  Builder.push("adrenaline")
  val MaxAdrenaline: ConfigValue[Double] = Builder
    .comment(
      "Maximum temporary adrenaline reserve. Set to zero to disable all adrenaline grants.",
      "Per-damage-source grant amounts are defined by adrenaline_rule datapack entries."
    )
    .defineInRange("maxValue", 100.0, 0.0, 10000.0, classOf[Double])
  val AdrenalineDecayPerTick: ConfigValue[Double] = Builder
    .comment(
      "Adrenaline removed per server tick after the combat grace window expires.",
      "The default 0.1 removes 2 points per second."
    )
    .defineInRange("decayPerTick", 0.1, 0.000001, 1000.0, classOf[Double])
  val AdrenalineCombatGraceTicks: ConfigValue[Integer] = Builder
    .comment(
      "Ticks after the latest positive adrenaline stimulus before decay starts.",
      "Repeated accepted hits refresh this grace window; 100 ticks is 5 seconds."
    )
    .defineInRange("combatGraceTicks", 100, 0, 72000)
  val AdrenalineShockProtectionPerPoint: ConfigValue[Double] = Builder
    .comment(
      "Temporary pain-shock threshold added per adrenaline point.",
      "Effective collapse threshold = shockCollapseThreshold + adrenaline * this value.",
      "Set to zero to keep the reserve visible to APIs while disabling shock protection."
    )
    .defineInRange("shockProtectionPerPoint", 1.0, 0.0, 100.0, classOf[Double])
  val AdrenalinePainReductionPerPoint: ConfigValue[Double] = Builder
    .comment(
      "Fraction of acute injury pain prevented per adrenaline point.",
      "The default 0.005 means 10 reserve prevents 5% of pain from the same accepted hit."
    )
    .defineInRange("painReductionPerPoint", 0.005, 0.0, 1.0, classOf[Double])
  val AdrenalineMaxPainReductionFraction: ConfigValue[Double] = Builder
    .comment(
      "Maximum fraction of acute injury pain that adrenaline can prevent.",
      "This never changes tissue damage, bleeding, or condition onset."
    )
    .defineInRange("maxPainReductionFraction", 0.5, 0.0, 1.0, classOf[Double])
  Builder.pop()

  Builder.push("opioid")
  val OpioidLevelDecayPerTick: ConfigValue[Double] = Builder
    .comment(
      "Acute opioid level removed per server tick. The default drains a full 200-point level",
      "in about 20 minutes (20 ticks = 1 second)."
    )
    .defineInRange("levelDecayPerTick", 0.0083, 0.0, 200.0, classOf[Double])
  val OpioidDependenceExposurePerLevelPerTick: ConfigValue[Double] = Builder
    .comment(
      "Dependence gained per stored opioid-level point per tick.",
      "The default makes one isolated 100-point exposure contribute approximately 7.5 dependence."
    )
    .defineInRange("dependenceExposurePerLevelPerTick", 0.0000125, 0.0, 1.0, classOf[Double])
  val OpioidDependenceDecayPerTick: ConfigValue[Double] = Builder
    .comment(
      "Dependence removed per tick, including while acute opioid exposure adds dependence.",
      "The default is 3 points per Minecraft day (24000 ticks)."
    )
    .defineInRange("dependenceDecayPerTick", 0.000125, 0.0, 100.0, classOf[Double])
  val OpioidAnalgesiaFormula: FormulaConfigValue = new FormulaConfigValue(
    Builder,
    "analgesiaFormula",
    "min(0.85, level / 150) / (1 + dependence / 100)",
    List("level", "dependence"),
    comment = Seq(
      "Fraction of aggregated pain masked before pain-shock progression.",
      "Available variables: level (0-200), dependence (0-100). Limb pain storage and medical",
      "display values remain unchanged. Invalid formulas fall back to the default."
    )
  )
  val OpioidExcitementStartLevel: ConfigValue[Double] = Builder
    .comment("Reserved excitement-band lower bound. Phase 1 assigns no mechanical effect to it.")
    .defineInRange("excitementStartLevel", 50.0, 0.0, 200.0, classOf[Double])
  val OpioidExcitementEndLevel: ConfigValue[Double] = Builder
    .comment("Reserved excitement-band upper bound. Phase 1 assigns no mechanical effect to it.")
    .defineInRange("excitementEndLevel", 120.0, 0.0, 200.0, classOf[Double])
  val OpioidSedationCeilingFormula: FormulaConfigValue = new FormulaConfigValue(
    Builder,
    "sedationCeilingFormula",
    "100 - 0.5 * max(0, level - 110)",
    List("level"),
    comment = Seq(
      "Opioid-derived consciousness ceiling.",
      "Available variable: level (0-200). The result is clamped to the consciousness range."
    )
  )
  val OpioidRespiratoryEfficiencyFormula: FormulaConfigValue = new FormulaConfigValue(
    Builder,
    "respiratoryEfficiencyFormula",
    "1 - 0.9 * max(0, level - (120 + min(20, dependence * 0.2))) / 80",
    List("level", "dependence"),
    comment = Seq(
      "Respiratory efficiency used to scale oxygen recovery.",
      "Available variables: level (0-200), dependence (0-100). Dependence moves onset right by",
      "0.2 level per point, capped at 20. The result is clamped to 0-1."
    )
  )
  val OpioidRespiratoryFailureEfficiencyThreshold: ConfigValue[Double] = Builder
    .comment(
      "Respiratory efficiency below which oxygen drains even with vanilla air available.",
      "The comparison is strict: efficiency equal to this threshold is not respiratory failure."
    )
    .defineInRange("respiratoryFailureEfficiencyThreshold", 0.3, 0.0, 1.0, classOf[Double])
  val OpioidRespiratoryFailureOxygenDrainPerTick: ConfigValue[Double] = Builder
    .comment("Blood oxygen drained per tick during opioid respiratory failure.")
    .defineInRange("respiratoryFailureOxygenDrainPerTick", 0.3, 0.0, 100.0, classOf[Double])
  val OpioidWithdrawalDependenceThreshold: ConfigValue[Double] = Builder
    .comment("Dependence must be strictly above this value for withdrawal to become active.")
    .defineInRange("withdrawalDependenceThreshold", 20.0, 0.0, 100.0, classOf[Double])
  val OpioidWithdrawalLevelPerDependence: ConfigValue[Double] = Builder
    .comment("Withdrawal is active while opioid level is below dependence times this factor.")
    .defineInRange("withdrawalLevelPerDependence", 0.6, 0.0, 2.0, classOf[Double])
  val OpioidWithdrawalPainMultiplier: ConfigValue[Double] = Builder
    .comment("Multiplier applied to acute injury and walking-strain pain during withdrawal.")
    .defineInRange("withdrawalPainMultiplier", 1.25, 0.0, 10.0, classOf[Double])
  val OpioidWithdrawalDiscomfortPerTick: ConfigValue[Double] = Builder
    .comment("Discomfort gained per tick during withdrawal (0.0025 = 0.05 per second).")
    .defineInRange("withdrawalDiscomfortPerTick", 0.0025, 0.0, 100.0, classOf[Double])
  val OpioidWithdrawalDiscomfortTarget: ConfigValue[Double] = Builder
    .comment("Withdrawal discomfort climbs toward, but never beyond, this value.")
    .defineInRange("withdrawalDiscomfortTarget", 35.0, 0.0, 100.0, classOf[Double])
  val OpioidRefinedSyringeDose: ConfigValue[Double] = Builder
    .comment("Base opioid dose drawn from one refined poppy ampoule.")
    .defineInRange("refinedSyringeDose", 50.0, 0.0, 200.0, classOf[Double])
  val OpioidCrudeSyringeDoseMean: ConfigValue[Double] = Builder
    .comment("Mean opioid dose sampled when drawing directly from crude poppy liquid.")
    .defineInRange("crudeSyringeDoseMean", 40.0, 0.0, 200.0, classOf[Double])
  val OpioidCrudeSyringeDoseSigma: ConfigValue[Double] = Builder
    .comment("Standard deviation of the normal crude-poppy dose sample.")
    .defineInRange("crudeSyringeDoseSigma", 13.0, 0.0, 200.0, classOf[Double])
  val OpioidCrudeSyringeDoseMinimum: ConfigValue[Double] = Builder
    .comment("Minimum crude-poppy base dose after normal sampling.")
    .defineInRange("crudeSyringeDoseMinimum", 20.0, 0.0, 200.0, classOf[Double])
  val OpioidCrudeSyringeDoseMaximum: ConfigValue[Double] = Builder
    .comment("Maximum crude-poppy base dose after normal sampling.")
    .defineInRange("crudeSyringeDoseMaximum", 60.0, 0.0, 200.0, classOf[Double])
  val OpioidUnmarkedSyringeJitterFraction: ConfigValue[Double] = Builder
    .comment("Maximum uniform measurement error fraction applied by an unmarked syringe.")
    .defineInRange("unmarkedSyringeJitterFraction", 0.15, 0.0, 1.0, classOf[Double])
  Builder.pop()

  Builder.push("injection")
  val InjectionMaxSpeedFractionPerSecond: ConfigValue[Double] = Builder
    .comment(
      "Fraction of a syringe pushed per second at full plunger press in the injection screen.",
      "Applies to every injectable; dose always lands proportionally to the pushed amount."
    )
    .defineInRange("maxSpeedFractionPerSecond", 0.5, 0.01, 10.0, classOf[Double])
  val InjectionFullDoseSideEffectDiscomfort: ConfigValue[Double] = Builder
    .comment(
      "Discomfort gained when one full syringe is pushed at maximum speed.",
      "Scales linearly with push speed and pushed amount."
    )
    .defineInRange("fullDoseSideEffectDiscomfort", 10.0, 0.0, 100.0, classOf[Double])
  val InjectionFullDoseSideEffectPain: ConfigValue[Double] = Builder
    .comment(
      "Injection-site pain gained when one full syringe is pushed at maximum speed.",
      "Scales linearly with push speed and pushed amount."
    )
    .defineInRange("fullDoseSideEffectPain", 10.0, 0.0, 100.0, classOf[Double])
  val InjectionFullSpeedPressDepthPixels: ConfigValue[Integer] = Builder
    .comment(
      "Screen pixels of plunger press depth (cursor below the thumb pad) that request maximum",
      "injection speed."
    )
    .defineInRange("fullSpeedPressDepthPixels", 60, 10, 500)
  val InjectionBatchIntervalMilliseconds: ConfigValue[Integer] = Builder
    .comment("Milliseconds between injection progress batches sent to the server.")
    .defineInRange("batchIntervalMilliseconds", 200, 20, 5000)
  Builder.pop()

  Builder.push("fall")
  val FallDamageFormula: FormulaConfigValue = new FormulaConfigValue(
    Builder,
    "damageFormula",
    "max(0, distance - safeDistance)^1.5 * 0.5 * modifier * multiplier",
    List("distance", "safeDistance", "modifier", "multiplier"),
    comment = Seq(
      "Fall damage formula, compiled with EvalEx (https://github.com/ezylang/EvalEx).",
      "Available variables: distance (fall distance), safeDistance (safe fall distance attribute),",
      "modifier (vanilla damage modifier), multiplier (fall damage multiplier attribute).",
      "Invalid formulas are rejected and corrected to the default. Hot-reloaded on file change."
    )
  )
  val AffectAllLivingEntities: ConfigValue[Boolean] = Builder
    .comment(
      "Whether the custom fall damage formula applies to all living entities, not just players."
    )
    .gameRestart()
    .define("affectAllLivingEntities", false)
  val BootsCushionFormula: FormulaConfigValue = new FormulaConfigValue(
    Builder,
    "bootsCushionFormula",
    "min(0.5, armor * 0.07 + toughness * 0.04)",
    List("armor", "toughness"),
    comment = Seq(
      "Fraction of the fall impact on the legs absorbed by worn boots (0 = none), compiled with",
      "EvalEx. Available variables: armor, toughness (the boots' attribute values in the feet slot).",
      "Boots with neither attribute cushion nothing. Invalid formulas are rejected and corrected",
      "to the default. Hot-reloaded on file change."
    )
  )
  val LeggingsConditionProtectionFormula: FormulaConfigValue = new FormulaConfigValue(
    Builder,
    "leggingsConditionProtectionFormula",
    "min(0.6, armor * 0.06 + toughness * 0.05)",
    List("armor", "toughness"),
    comment = Seq(
      "Fraction of the fall impact ignored when rolling the dislocation/fracture thresholds,",
      "from worn leggings, compiled with EvalEx. Available variables: armor, toughness (the",
      "leggings' attribute values in the legs slot). Invalid formulas are rejected and corrected",
      "to the default. Hot-reloaded on file change."
    )
  )
  Builder.pop()

  Builder.push("temperature")
  val TauAirMinutes: ConfigValue[Double] = Builder
    .comment(
      "Time constant (minutes) of core-temperature approach in still air: every tau, the",
      "remaining gap to the equilibrium closes by ~63%. Initial placeholder, pending calibration."
    )
    .defineInRange("tauAirMinutes", 3.0, 0.1, 60.0, classOf[Double])
  val ImmersionRateMultiplier: ConfigValue[Double] = Builder
    .comment(
      "Multiplier on the approach rate while immersed in water (water conducts heat far better",
      "than air). Initial placeholder, pending calibration."
    )
    .defineInRange("immersionRateMultiplier", 2.0, 1.0, 10.0, classOf[Double])
  val ComfortLowCelsius: ConfigValue[Double] = Builder
    .comment(
      "Lower bound of the comfort band (°C): at or above it, the equilibrium core temperature is",
      "normal body temperature. Initial placeholder, pending calibration."
    )
    .defineInRange("comfortLowCelsius", 10.0, -50.0, 37.0, classOf[Double])
  val ComfortHighCelsius: ConfigValue[Double] = Builder
    .comment(
      "Upper bound of the comfort band (°C): at or below it, the equilibrium core temperature is",
      "normal body temperature. Initial placeholder, pending calibration."
    )
    .defineInRange("comfortHighCelsius", 28.0, -50.0, 80.0, classOf[Double])
  val ComfortSlope: ConfigValue[Double] = Builder
    .comment(
      "How strongly the equilibrium core temperature deviates per °C of apparent temperature",
      "outside the comfort band. Initial placeholder, pending calibration."
    )
    .defineInRange("comfortSlope", 0.3, 0.0, 1.0, classOf[Double])
  val PenaltyBandLowCelsius: ConfigValue[Double] = Builder
    .comment(
      "Lower bound (°C) of the body-temperature penalty band: below it, consciousness is",
      "capped and immune health drains. Initial placeholder, pending calibration."
    )
    .defineInRange("penaltyBandLowCelsius", 35.0, 0.0, 37.0, classOf[Double])
  val PenaltyBandHighCelsius: ConfigValue[Double] = Builder
    .comment(
      "Upper bound (°C) of the body-temperature penalty band: above it, consciousness is capped",
      "and immune health drains. Initial placeholder, pending calibration."
    )
    .defineInRange("penaltyBandHighCelsius", 39.5, 37.0, 45.0, classOf[Double])
  val ColdConsciousnessSlopePerDegree: ConfigValue[Double] = Builder
    .comment(
      "Consciousness-ceiling reduction per °C of cold-side deviation below the penalty band.",
      "Initial placeholder, pending calibration."
    )
    .defineInRange("coldConsciousnessSlopePerDegree", 5.0, 0.0, 50.0, classOf[Double])
  val HotConsciousnessSlopePerDegree: ConfigValue[Double] = Builder
    .comment(
      "Consciousness-ceiling reduction per °C of hot-side deviation above the penalty band.",
      "Initial placeholder, pending calibration."
    )
    .defineInRange("hotConsciousnessSlopePerDegree", 5.0, 0.0, 50.0, classOf[Double])
  val ColdImmuneDrainPerDegreePerMinute: ConfigValue[Double] = Builder
    .comment(
      "Immune-health drain per minute per °C of cold-side deviation below the penalty band",
      "(cold suppresses immunity harder than heat). Initial placeholder, pending calibration."
    )
    .defineInRange("coldImmuneDrainPerDegreePerMinute", 1.0, 0.0, 20.0, classOf[Double])
  val HotImmuneDrainPerDegreePerMinute: ConfigValue[Double] = Builder
    .comment(
      "Immune-health drain per minute per °C of hot-side deviation above the penalty band.",
      "Initial placeholder, pending calibration."
    )
    .defineInRange("hotImmuneDrainPerDegreePerMinute", 0.5, 0.0, 20.0, classOf[Double])
  val SweatCoreTempThreshold: ConfigValue[Double] = Builder
    .comment(
      "Core temperature (°C) above which exertion produces sweat (wetness). Initial placeholder,",
      "pending calibration."
    )
    .defineInRange("sweatCoreTempThreshold", 37.0, 30.0, 45.0, classOf[Double])
  val SweatWetnessPerSecond: ConfigValue[Double] = Builder
    .comment(
      "Wetness produced per second at full sprint exertion while the core temperature is above",
      "the sweat threshold. Initial placeholder, pending calibration."
    )
    .defineInRange("sweatWetnessPerSecond", 0.15, 0.0, 1.0, classOf[Double])
  val SweatDirtinessMultiplier: ConfigValue[Double] = Builder
    .comment(
      "Multiplier on passive dirtiness accrual while sweating (sweat gathers grime). Stacked",
      "multiplicatively with the other situational multipliers. Initial placeholder, pending",
      "calibration."
    )
    .defineInRange("sweatDirtinessMultiplier", 1.5, 1.0, 10.0, classOf[Double])
  val EvaporationCoolingPerMinute: ConfigValue[Double] = Builder
    .comment(
      "Maximum evaporative cooling (°C/min) at full wetness in fully dry air; scales with wetness",
      "and air dryness (1 - downfall). Initial placeholder, pending calibration."
    )
    .defineInRange("evaporationCoolingPerMinute", 0.3, 0.0, 5.0, classOf[Double])
  val ExerciseHeatPerExhaustionPerSecond: ConfigValue[Double] = Builder
    .comment(
      "Core-temperature gain (°C/min) per unit of vanilla exhaustion accumulated per second.",
      "Initial placeholder, pending calibration."
    )
    .defineInRange("exerciseHeatPerExhaustionPerSecond", 0.9, 0.0, 10.0, classOf[Double])
  val OnFireHeatPerMinute: ConfigValue[Double] = Builder
    .comment(
      "Direct contact heat (°C/min) while on fire or inside a fire block. Initial placeholder,",
      "pending calibration."
    )
    .defineInRange("onFireHeatPerMinute", 3.0, 0.0, 100.0, classOf[Double])
  val LavaContactHeatPerMinute: ConfigValue[Double] = Builder
    .comment(
      "Direct contact heat (°C/min) while touching lava. Initial placeholder, pending calibration."
    )
    .defineInRange("lavaContactHeatPerMinute", 10.0, 0.0, 1000.0, classOf[Double])
  val HeatSourceBlockHeatPerMinute: ConfigValue[Double] = Builder
    .comment(
      "Direct contact heat (°C/min) while standing on a heat-source block (magma block, lit",
      "campfire). Initial placeholder, pending calibration."
    )
    .defineInRange("heatSourceBlockHeatPerMinute", 1.5, 0.0, 100.0, classOf[Double])
  val FireDryingBonusDegrees: ConfigValue[Double] = Builder
    .comment(
      "Apparent-temperature bonus (°C) fed to the drying curve while on fire; dries wetness in",
      "seconds. Only affects drying, never core temperature. Initial placeholder, pending",
      "calibration."
    )
    .defineInRange("fireDryingBonusDegrees", 60.0, 0.0, 1000.0, classOf[Double])
  val ImmersionWetnessPerSecond: ConfigValue[Double] = Builder
    .comment(
      "Wetness gain per second while immersed in water. Initial placeholder, pending calibration."
    )
    .defineInRange("immersionWetnessPerSecond", 0.5, 0.0, 1.0, classOf[Double])
  val RainWetnessPerSecond: ConfigValue[Double] = Builder
    .comment(
      "Wetness gain per second while exposed to rain. Initial placeholder, pending calibration."
    )
    .defineInRange("rainWetnessPerSecond", 0.02, 0.0, 1.0, classOf[Double])
  val BiomeMappingFormula: FormulaConfigValue = new FormulaConfigValue(
    Builder,
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
  )
  val ComfortBandFormula: FormulaConfigValue = new FormulaConfigValue(
    Builder,
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
  )
  val EffectiveTemperatureFormula: FormulaConfigValue = new FormulaConfigValue(
    Builder,
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
  )
  val TemperatureConsciousnessCeilingFormula: FormulaConfigValue = new FormulaConfigValue(
    Builder,
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
  )
  val DryingCurveFormula: FormulaConfigValue = new FormulaConfigValue(
    Builder,
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
  )
  val WetnessCollapseFormula: FormulaConfigValue = new FormulaConfigValue(
    Builder,
    "wetnessCollapseFormula",
    TemperatureCalc.WetnessCollapseFormulaDefault,
    List("wetness"),
    comment = Seq(
      "Fraction of armor thermal coefficients (insulation and dissipation block) that survives at",
      "a given wetness, compiled with EvalEx. Available variable: wetness (0..1). Wet armor both",
      "stops insulating and stops trapping sweat. Initial placeholder, pending calibration.",
      "Invalid formulas are rejected and corrected to the default. Hot-reloaded on file change."
    )
  )
  val TemperatureOverlayEnabled: ConfigValue[Boolean] = Builder
    .comment(
      "Temperature screen effects: a vitals post-shader axis. Cold side grows a frost overlay",
      "inward from the screen edges (vanilla's powder-snow texture with a spatial mask); hot side",
      "adds heat-haze wobble and a warm edge tint. Client-side presentation only; no gameplay",
      "effect."
    )
    .define("temperatureOverlayEnabled", true)
  val FrostOverlayStartCelsius: ConfigValue[Double] = Builder
    .comment(
      "Core body temperature (°C) at which the frost overlay starts.",
      "Initial placeholder, pending calibration. Client-side presentation only."
    )
    .defineInRange("frostOverlayStartCelsius", 35.0, 20.0, 37.0, classOf[Double])
  val FrostOverlayFullSpanCelsius: ConfigValue[Double] = Builder
    .comment(
      "Degrees below frostOverlayStartCelsius at which the frost overlay reaches its maximum",
      "strength. Initial placeholder, pending calibration. Client-side presentation only."
    )
    .defineInRange("frostOverlayFullSpanCelsius", 6.0, 1.0, 20.0, classOf[Double])
  val FrostOverlayMaxStrength: ConfigValue[Double] = Builder
    .comment(
      "Maximum frost overlay strength (0..1) reached at the full span below the onset.",
      "Initial placeholder, pending calibration. Client-side presentation only."
    )
    .defineInRange("frostOverlayMaxStrength", 0.85, 0.0, 1.0, classOf[Double])
  val HeatOverlayStartCelsius: ConfigValue[Double] = Builder
    .comment(
      "Core body temperature (°C) at which the heat overlay (haze + warm tint) starts.",
      "Initial placeholder, pending calibration. Client-side presentation only."
    )
    .defineInRange("heatOverlayStartCelsius", 39.5, 37.0, 45.0, classOf[Double])
  val HeatOverlayFullSpanCelsius: ConfigValue[Double] = Builder
    .comment(
      "Degrees above heatOverlayStartCelsius at which the heat overlay reaches its maximum",
      "strength — anchored so the maximum lands on the terminal-band edge (heatstroke).",
      "Initial placeholder, pending calibration. Client-side presentation only."
    )
    .defineInRange("heatOverlayFullSpanCelsius", 2.5, 0.5, 10.0, classOf[Double])
  val HeatOverlayMaxStrength: ConfigValue[Double] = Builder
    .comment(
      "Maximum heat overlay strength (0..1) reached at the full span above the onset.",
      "Initial placeholder, pending calibration. Client-side presentation only."
    )
    .defineInRange("heatOverlayMaxStrength", 0.85, 0.0, 1.0, classOf[Double])
  Builder.pop()

  Builder.push("progression")
  val NotTodayNearMaxBleedingFraction: ConfigValue[Double] = Builder
    .comment(
      "Near-maximum hemorrhage threshold of the \"Not Today\" advancement, as a fraction of a",
      "limb's maximum external bleeding rate (maxExternalBleedingRate). An episode arms once the",
      "player's total external bleeding rate reaches this threshold, and completes when external",
      "bleeding is fully stopped while the player lives to see it."
    )
    .defineInRange("notTodayNearMaxBleedingFraction", 0.75, 0.0, 1.0, classOf[Double])
  Builder.pop()

  private val Spec = Builder.build()

  /** Immune health at which infection spread and immune fight exactly cancel out for a single
    * infection: `max × spread / (spread + fight)`. Below it infections spread, above it they
    * recede. With several infected limbs the fight capacity is split, so the effective break-even
    * rises with the infection count.
    */
  def immuneBreakEven: Double = {
    val spread = InfectionSpreadPerTick.get()
    val fight = InfectionFightPerTick.get()
    if (spread + fight <= 0.0) {
      0.0
    } else {
      MaxImmuneHealth.get() * spread / (spread + fight)
    }
  }

  /** The effective blood volume cap: sepsis compresses it linearly, down to zero at full sepsis
    * (which is fatal). Blood over the cap is lost — recovering requires eating well (see
    * [[FedBloodRegenPerTick]]).
    */
  def effectiveMaxBloodVolume(sepsis: Double): Double = {
    MaxBloodVolume.get() * (1.0 - (sepsis / MaxSepsis.get()).min(1.0))
  }

  /** Cross-field consciousness thresholds used by server progression and synchronized displays. */
  private[casualtiesbelow] def effectiveConsciousnessFloor: Double = {
    finiteThreshold(ConsciousnessFloor.get(), 0.0)
  }

  private[casualtiesbelow] def effectiveConsciousnessKnockoutThreshold: Double = {
    finiteThreshold(ConsciousnessKnockoutThreshold.get(), effectiveConsciousnessFloor)
  }

  private[casualtiesbelow] def effectiveConsciousnessWakeThreshold: Double = {
    val knockout = effectiveConsciousnessKnockoutThreshold
    val minimumWake =
      (knockout + VitalsComponent.MinimumWakeThreshold).min(VitalsComponent.MaxValue)
    finiteThreshold(ConsciousnessWakeThreshold.get(), minimumWake)
  }

  private def finiteThreshold(
      value: scala.Double,
      minimum: scala.Double
  ): scala.Double = {
    if (value == scala.Double.PositiveInfinity) VitalsComponent.MaxValue
    else if (value.isFinite) value.max(minimum).min(VitalsComponent.MaxValue)
    else minimum
  }

  def register(): Unit = {
    ConfigRegistry.INSTANCE.register(CasualtiesBelow.ModId, ModConfig.Type.COMMON, Spec)
    migratePhysiologyBalanceDefaults()
  }

  /** FCAP preserves every existing valid value when only a spec default changes. Migrate values
    * that still equal the previous release defaults, while retaining genuinely customized values.
    * The marker starts at zero so both a pre-marker file and a fresh file take this idempotent
    * pass; fresh files already contain the new values and therefore only advance the marker.
    */
  private def migratePhysiologyBalanceDefaults(): Unit = {
    if (PhysiologyBalanceVersion.get().intValue >= CurrentPhysiologyBalanceVersion) return

    migratePreviousDefault(ConsciousnessIncapacitationStartThreshold, 30.0, 50.0)
    migratePreviousDefault(BloodOxygenDepletionPerTick, 0.3, 0.4)
    migratePreviousDefault(BloodOxygenRecoveryPerTick, 0.5, 0.8)
    migratePreviousDefault(ConsciousnessRecoveryPerTick, 0.08, 0.2)
    migratePreviousDefault(ConsciousnessWakeThreshold, 20.0, 40.0)
    migratePreviousDefault(InWallBloodOxygenDepletionPerTick, 0.5, 0.6)
    if (TerminalHypoxiaDurationTicks.get().intValue == 200) {
      TerminalHypoxiaDurationTicks.set(160)
    }

    PhysiologyBalanceVersion.set(CurrentPhysiologyBalanceVersion)
    Spec.save()
    CasualtiesBelow.Logger.info(
      "Migrated physiology balance defaults to version {}",
      CurrentPhysiologyBalanceVersion
    )
  }

  private def migratePreviousDefault(
      value: ConfigValue[Double],
      previousDefault: Double,
      currentDefault: Double
  ): Unit = {
    if (
      java.lang.Double.doubleToLongBits(value.get().doubleValue) ==
        java.lang.Double.doubleToLongBits(previousDefault)
    ) {
      value.set(currentDefault)
    }
  }
}
