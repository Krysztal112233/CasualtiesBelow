package dev.krysztal.casualtiesbelow.config

import java.lang.Boolean
import java.lang.Double
import java.lang.Integer

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.discomfort.DiscomfortDistribution as Distribution
import dev.krysztal.casualtiesbelow.pain.TotalPainStrategy

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

/** Common (server-authoritative) configuration, backed by Forge Config API Port. Values are written
  * to `config/casualtiesbelow-common.toml` and can be edited in-game via the ModMenu integration
  * provided by Forge Config API Port.
  */
object CasualtiesBelowConfig {

  private val Builder = new ModConfigSpec.Builder()

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
  val ConsciousnessBlackoutThreshold: ConfigValue[Double] = Builder
    .comment(
      "Consciousness at or below which the dimming is strongest and slowly pulses.",
      "Must not exceed consciousnessDimThreshold to take full effect."
    )
    .defineInRange("consciousnessBlackoutThreshold", 30.0, 0.0, 100.0, classOf[Double])
  val ConsciousnessMaxDimOpacity: ConfigValue[Double] = Builder
    .comment(
      "Strongest edge-darkening strength (0.0-1.0), reached at the blackout threshold.",
      "The center haze stays weaker; 0 disables the effect."
    )
    .defineInRange("consciousnessMaxDimOpacity", 0.55, 0.0, 1.0, classOf[Double])
  val ConsciousnessMaxBlurStrength: ConfigValue[Double] = Builder
    .comment(
      "Strongest zoom blur and double-vision strength (0.0-1.0), reached at zero consciousness.",
      "The effect begins below consciousnessBlackoutThreshold; 0 disables it."
    )
    .defineInRange("consciousnessMaxBlurStrength", 0.99, 0.0, 1.0, classOf[Double])
  val MaxBloodVolume: ConfigValue[Double] = Builder
    .comment(
      "Total blood volume of a player, in mL; bleeding drains it and reaching zero is fatal."
    )
    .gameRestart()
    .defineInRange("maxBloodVolume", 5000.0, 100.0, 100000.0, classOf[Double])
  val FedBloodRegenPerTick: ConfigValue[Double] = Builder
    .comment(
      "Blood volume regenerated per tick while well-fed (same food threshold as immune",
      "regeneration), capped by the effective maximum blood volume. A survivor of sepsis or",
      "heavy bleeding has to eat well to recover."
    )
    .defineInRange("fedBloodRegenPerTick", 0.5, 0.0, 100.0, classOf[Double])
  val MaxImmuneHealth: ConfigValue[Double] = Builder
    .comment(
      "Maximum (and starting) immune health. With the default infection rates, the break-even",
      "point where the immune system exactly matches infection spread is 120 out of 200."
    )
    .gameRestart()
    .defineInRange("maxImmuneHealth", 200.0, 1.0, 10000.0, classOf[Double])
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
      "At the default, a fresh sword cut (0.2 mL/tick) clots shut in 50 seconds."
    )
    .defineInRange("clottingRatePerTick", 0.0002, 0.0, 1.0, classOf[Double])
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

  // Wound profiles: how each damage kind splits into skin/muscle loss, bleeding, and pain.
  // Classification (which profile a damage source gets) is datapack-tag driven where it is
  // content — see CasualtiesBelowTags and WoundProfiles.classify.
  Builder.push("wounds")
  val BiteWound: WoundProfileConfig = woundProfile(
    "bite",
    skin = 1.5,
    muscle = 2.0,
    bleed = 0.1,
    pain = 4.0,
    "Unarmed bite/scratch hits (most bare-handed mobs): teeth and claws break skin lightly"
  )
  val CutWound: WoundProfileConfig = woundProfile(
    "cut",
    skin = 2.0,
    muscle = 3.0,
    bleed = 0.2,
    pain = 4.0,
    "Sharp-weapon melee hits (the casualtiesbelow:sharp_melee item tag)"
  )
  val BluntWound: WoundProfileConfig = woundProfile(
    "blunt",
    skin = 0.0,
    muscle = 3.0,
    bleed = 0.0,
    pain = 4.0,
    "Blunt hits (non-sharp weapons, slam attackers in casualtiesbelow:blunt_melee, sonic boom):",
    "muscle only, skin intact"
  )
  val PierceWound: WoundProfileConfig = woundProfile(
    "pierce",
    skin = 3.0,
    muscle = 2.0,
    bleed = 0.15,
    pain = 5.0,
    "Piercing projectiles and similar (arrows, tridents, shulker bullets, evoker fangs)"
  )
  val BurnWound: WoundProfileConfig = woundProfile(
    "burn",
    skin = 1.0,
    muscle = 0.2,
    bleed = 0.0,
    pain = 2.0,
    "Fire damage (standing in fire/lava, fireballs): burns wreck the skin but cauterize —",
    "no bleeding; the damaged skin is an infection gateway"
  )
  val PrickWound: WoundProfileConfig = woundProfile(
    "prick",
    skin = 1.5,
    muscle = 0.0,
    bleed = 0.05,
    pain = 1.0,
    "Environmental pricks (cactus, sweet berry bushes): thorns scratch skin but never reach",
    "muscle — zero muscle damage so contact spam cannot melt it"
  )
  val BlastWound: WoundProfileConfig = woundProfile(
    "blast",
    skin = 2.0,
    muscle = 2.0,
    bleed = 0.25,
    pain = 6.0,
    "Explosions (creepers, ghast fireball AoE, wither skulls): shrapnel scatters the damage",
    "across a few random body parts"
  )
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

  private val Spec = Builder.build()

  /** One wound profile's worth of coefficients (see the `wounds.*` config sections). */
  final case class WoundProfileConfig(
      skinPerPoint: ConfigValue[Double],
      musclePerPoint: ConfigValue[Double],
      bleedRatePerWound: ConfigValue[Double],
      painPerPoint: ConfigValue[Double]
  )

  private def woundProfile(
      name: String,
      skin: Double,
      muscle: Double,
      bleed: Double,
      pain: Double,
      commentLines: String*
  ): WoundProfileConfig = {
    Builder.push(name)
    val skinValue = Builder
      .comment((commentLines :+ "Skin integrity lost per half-heart of damage.").toArray*)
      .defineInRange("skinPerPoint", skin, 0.0, 100.0, classOf[Double])
    val muscleValue = Builder
      .comment(Array("Muscle health lost per half-heart of damage.")*)
      .defineInRange("musclePerPoint", muscle, 0.0, 100.0, classOf[Double])
    val bleedValue = Builder
      .comment(Array("External bleeding rate (mL/tick) granted per wound; zero = no bleeding.")*)
      .defineInRange("bleedRatePerWound", bleed, 0.0, 10.0, classOf[Double])
    val painValue = Builder
      .comment(Array("Pain granted per half-heart of damage.")*)
      .defineInRange("painPerPoint", pain, 0.0, 100.0, classOf[Double])
    Builder.pop()
    WoundProfileConfig(skinValue, muscleValue, bleedValue, painValue)
  }

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

  def register(): Unit =
    ConfigRegistry.INSTANCE.register(CasualtiesBelow.ModId, ModConfig.Type.COMMON, Spec)
}
