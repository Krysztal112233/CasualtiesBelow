package dev.krysztal.casualtiesbelow.config.sections

import java.lang.Double

import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

private[config] final case class DirtinessValues(
    maxValue: ConfigValue[Double],
    accrualPerSecond: ConfigValue[Double],
    sprintMultiplier: ConfigValue[Double],
    armoredMultiplier: ConfigValue[Double],
    netherMultiplier: ConfigValue[Double],
    washWaterPerSecond: ConfigValue[Double],
    washRainPerSecond: ConfigValue[Double],
    cauldronPointsPerLevel: ConfigValue[Double],
    dirtyWaterWashMultiplier: ConfigValue[Double],
    combatPulseDirt: ConfigValue[Double],
    diggingPulseDirt: ConfigValue[Double],
    interactionPulseDirt: ConfigValue[Double],
    foodDirtFraction: ConfigValue[Double],
    infectionChanceMultiplierAtMax: ConfigValue[Double],
    injectionSeedAtMax: ConfigValue[Double],
    foodDiscomfortMultiplierAtMax: ConfigValue[Double],
    immuneDrainStartDirtiness: ConfigValue[Double],
    immuneDrainMaxPerTick: ConfigValue[Double]
)

private[config] object DirtinessValues {

  def define(b: ModConfigSpec.Builder): DirtinessValues = {
    b.push("dirtiness")
    val s = DirtinessValues(
      maxValue = b
        .comment(
          "Maximum dirtiness value. Dirtiness is a whole-body hygiene axis (0 = clean): the",
          "environment and the player's own actions push it up, only water washes it down.",
          "The display bands (visuals.dirtinessBand*) only drive the screen presentation;",
          "every mechanic computes from the raw value."
        )
        .defineInRange("maxValue", 100.0, 1.0, 10000.0, classOf[Double]),
      accrualPerSecond = b
        .comment(
          "Passive dirtiness accrual per second. At the default, total neglect reaches the",
          "filthy display band (60) in ~33 minutes of play (about 1.7 in-game days)."
        )
        .defineInRange("accrualPerSecond", 0.03, 0.0, 1000.0, classOf[Double]),
      sprintMultiplier = b
        .comment("Accrual multiplier while sprinting (sweat). Multiplicative with the others.")
        .defineInRange("sprintMultiplier", 2.5, 0.0, 100.0, classOf[Double]),
      armoredMultiplier = b
        .comment("Accrual multiplier while wearing a full armor set (heat buildup).")
        .defineInRange("armoredMultiplier", 1.3, 0.0, 100.0, classOf[Double]),
      netherMultiplier = b
        .comment(
          "Accrual multiplier in Nether biomes (biome tag minecraft:is_nether): ash and heat,",
          "combining with vanilla's no-water rule to create hygiene pressure."
        )
        .defineInRange("netherMultiplier", 1.5, 0.0, 100.0, classOf[Double]),
      washWaterPerSecond = b
        .comment(
          "Dirtiness removed per second while immersed in water. At the default a fully dirty",
          "player is clean after ~21 seconds; washing always far outruns accrual."
        )
        .defineInRange("washWaterPerSecond", 4.8, 0.0, 1000.0, classOf[Double]),
      washRainPerSecond = b
        .comment("Dirtiness removed per second while exposed to rain: free but slow.")
        .defineInRange("washRainPerSecond", 0.6, 0.0, 1000.0, classOf[Double]),
      cauldronPointsPerLevel = b
        .comment(
          "Dirtiness washed per consumed water level while standing in a water cauldron. At",
          "the default one full cauldron (3 levels = 102 points) covers a full 0-100 wash.",
          "Cauldrons are the only legal water storage in the Nether."
        )
        .defineInRange("cauldronPointsPerLevel", 34.0, 0.0, 10000.0, classOf[Double]),
      dirtyWaterWashMultiplier = b
        .comment(
          "Wash-rate multiplier in murky water (biome tag casualtiesbelow:dirty_water, e.g.",
          "swamps): 0.5 = half as effective. 1.0 disables the distinction."
        )
        .defineInRange("dirtyWaterWashMultiplier", 0.5, 0.0, 1.0, classOf[Double]),
      combatPulseDirt = b
        .comment(
          "Dirtiness pulse of one zombie-family hit received; every combat event scales from it",
          "with fixed relative weights: explosions ×5/3, generic monster hits ×1/3, melee kills",
          "×0.8/3. Rolled with randomness.worldPulseJitter."
        )
        .defineInRange("combatPulseDirt", 3.0, 0.0, 1000.0, classOf[Double]),
      diggingPulseDirt = b
        .comment(
          "Dirtiness pulse per broken ordinary block (stone, ore and the like); loose blocks",
          "(block tag casualtiesbelow:dirty_diggable: dirt, sand, gravel) coat double, dustless",
          "blocks (casualtiesbelow:dustless_diggable: leaves, wool, wood, glass) raise nothing."
        )
        .defineInRange("diggingPulseDirt", 0.02, 0.0, 1000.0, classOf[Double]),
      interactionPulseDirt = b
        .comment(
          "Dirtiness pulse per animal-husbandry interaction (shearing, milking), rolled with",
          "randomness.worldPulseJitter."
        )
        .defineInRange("interactionPulseDirt", 0.5, 0.0, 1000.0, classOf[Double]),
      foodDirtFraction = b
        .comment(
          "Dirtiness pulse of a eaten food as a fraction of its discomfort tier mean (0.1 =",
          "rotten flesh adds 3, raw meat 1.5): the hygiene risk of contaminated food."
        )
        .defineInRange("foodDirtFraction", 0.1, 0.0, 10.0, classOf[Double]),
      infectionChanceMultiplierAtMax = b
        .comment(
          "Additional wound-infection chance multiplier at maximum dirtiness: the per-tick",
          "infection chance scales with (1 + value × dirtiness/maxValue). 2.0 = triple",
          "chance when fully dirty."
        )
        .defineInRange("infectionChanceMultiplierAtMax", 2.0, 0.0, 100.0, classOf[Double]),
      injectionSeedAtMax = b
        .comment(
          "Infection progress seeded into the injected limb by one full syringe at maximum",
          "dirtiness; scales linearly with dirtiness and with the pushed fraction. The",
          "default stays below the symptomatic threshold (infectionEffectStartProgress 20):",
          "a dirty needle alone cannot cause symptomatic infection."
        )
        .defineInRange("injectionSeedAtMax", 12.0, 0.0, 100.0, classOf[Double]),
      foodDiscomfortMultiplierAtMax = b
        .comment(
          "Additional food-discomfort dose fraction at maximum dirtiness (eating with dirty",
          "hands): the dose scales with (1 + value × dirtiness/maxValue)."
        )
        .defineInRange("foodDiscomfortMultiplierAtMax", 0.5, 0.0, 100.0, classOf[Double]),
      immuneDrainStartDirtiness = b
        .comment(
          "Dirtiness at which the continuous immune drain starts; the drain ramps linearly",
          "to immuneDrainMaxPerTick at maxValue."
        )
        .defineInRange("immuneDrainStartDirtiness", 50.0, 0.0, 10000.0, classOf[Double]),
      immuneDrainMaxPerTick = b
        .comment(
          "Immune health drained per tick at maximum dirtiness. The default equals",
          "hungryImmuneDrainPerTick (maximally dirty = starving): a full 200 immune drains",
          "in ~16.7 minutes, and at 75 dirtiness the drain exactly offsets",
          "fedImmuneRegenPerTick."
        )
        .defineInRange("immuneDrainMaxPerTick", 0.01, 0.0, 10.0, classOf[Double])
    )
    b.pop()
    s
  }
}
