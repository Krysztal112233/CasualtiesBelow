package dev.krysztal.casualtiesbelow.config.sections

import java.lang.Double

import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

private[config] final case class InfectionValues(
    infectionChancePerTick: ConfigValue[Double],
    infectionSpreadPerTick: ConfigValue[Double],
    infectionFightPerTick: ConfigValue[Double],
    skinRegenMinImmuneMultiplier: ConfigValue[Double],
    infectionEffectStartProgress: ConfigValue[Double],
    infectionEffectFullProgress: ConfigValue[Double],
    infectionPainPerTick: ConfigValue[Double],
    infectionMuscleDecayPerTick: ConfigValue[Double],
    infectionContagionStartProgress: ConfigValue[Double],
    infectionContagionFullProgress: ConfigValue[Double],
    infectionContagionMaxChancePerTick: ConfigValue[Double]
)

private[config] object InfectionValues {

  def define(b: ModConfigSpec.Builder): InfectionValues = {
    b.push("infection")
    val s = InfectionValues(
      infectionChancePerTick = b
        .comment(
          "Per-tick probability that a wound starts an infection, at zero skin integrity; scales",
          "linearly with the skin damage (half-intact skin: half the chance). Only wounds below",
          "the skin integrity threshold (20 damage, i.e. under 80) can get infected at all.",
          "0 disables infections."
        )
        .defineInRange("infectionChancePerTick", 0.0005, 0.0, 1.0, classOf[Double]),
      infectionSpreadPerTick = b
        .comment(
          "Infection progress gained per tick at zero immune health; scales down linearly and",
          "reaches zero at full immune health. 0.03 = an unchecked infection runs 0 to 100 in",
          "~2.8 min."
        )
        .defineInRange("infectionSpreadPerTick", 0.03, 0.0, 10.0, classOf[Double]),
      infectionFightPerTick = b
        .comment(
          "Infection progress removed per tick at full immune health; scales down linearly and",
          "reaches zero at zero immune health. With the defaults (spread 0.03, fight 0.02, max",
          "immune health 200), the break-even immune health is 120: above it infections recede,",
          "below it they spread."
        )
        .defineInRange("infectionFightPerTick", 0.02, 0.0, 10.0, classOf[Double]),
      skinRegenMinImmuneMultiplier = b
        .comment(
          "Skin regrowth multiplier at zero immune health, as a fraction of the base rate (0.25 =",
          "quarter speed); at full immune health skin regrows at the base rate. Never zero, so a",
          "dying player is not soft-locked out of healing."
        )
        .defineInRange("skinRegenMinImmuneMultiplier", 0.25, 0.0, 1.0, classOf[Double]),
      infectionEffectStartProgress = b
        .comment(
          "Infection progress at which local consequences (pain, muscle decay) begin; below this",
          "an infection is asymptomatic."
        )
        .defineInRange("infectionEffectStartProgress", 20.0, 0.0, 100.0, classOf[Double]),
      infectionEffectFullProgress = b
        .comment(
          "Infection progress at which local consequences reach full strength; between the start",
          "and this value the effect strength ramps up linearly."
        )
        .defineInRange("infectionEffectFullProgress", 40.0, 0.0, 100.0, classOf[Double]),
      infectionPainPerTick = b
        .comment(
          "Pain granted per tick by an infection at full effect strength (see",
          "infectionEffectFullProgress). At the default, a full-strength infection outruns natural",
          "pain decay, so the limb keeps hurting until the infection recedes."
        )
        .defineInRange("infectionPainPerTick", 0.05, 0.0, 10.0, classOf[Double]),
      infectionMuscleDecayPerTick = b
        .comment(
          "Muscle health destroyed per tick by an infection at full effect strength (see",
          "infectionEffectFullProgress). At the default (double the muscle regrowth rate), a",
          "full-strength infection wastes the limb away in about half an hour."
        )
        .defineInRange("infectionMuscleDecayPerTick", 0.005, 0.0, 10.0, classOf[Double]),
      infectionContagionStartProgress = b
        .comment(
          "Infection progress at which a limb can start seeding infections into anatomically",
          "adjacent limbs; below this it never spreads."
        )
        .defineInRange("infectionContagionStartProgress", 60.0, 0.0, 100.0, classOf[Double]),
      infectionContagionFullProgress = b
        .comment(
          "Infection progress at which the contagion chance reaches its maximum; between the",
          "start and this value the per-tick chance ramps up linearly."
        )
        .defineInRange("infectionContagionFullProgress", 80.0, 0.0, 100.0, classOf[Double]),
      infectionContagionMaxChancePerTick = b
        .comment(
          "Per-tick probability of seeding a random uninfected adjacent limb at full ramp",
          "(0.05 = on average one spread per second); the seeded limb starts with a small",
          "infection progress, like a fresh wound onset."
        )
        .defineInRange("infectionContagionMaxChancePerTick", 0.05, 0.0, 1.0, classOf[Double])
    )
    b.pop()
    s
  }
}
