package dev.krysztal.casualtiesbelow.config.sections

import java.lang.Double
import java.lang.Integer

import dev.krysztal.casualtiesbelow.physiology.discomfort.DiscomfortDistribution as Distribution

import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

private[config] final case class DiscomfortValues(
    maxValue: ConfigValue[Double],
    distribution: ModConfigSpec.EnumValue[Distribution],
    spreadFraction: ConfigValue[Double],
    level1Mean: ConfigValue[Double],
    level2Mean: ConfigValue[Double],
    level3Mean: ConfigValue[Double],
    nauseaThreshold: ConfigValue[Double],
    refusalThreshold: ConfigValue[Double],
    vomitChanceThreshold: ConfigValue[Double],
    vomitMinChancePerTick: ConfigValue[Double],
    vomitMaxChancePerTick: ConfigValue[Double],
    decayRateLowPerSecond: ConfigValue[Double],
    decayRateHighPerSecond: ConfigValue[Double],
    alreadyNauseousMultiplier: ConfigValue[Double],
    overeatingMultiplier: ConfigValue[Double],
    poorConditionMultiplier: ConfigValue[Double],
    poorConditionConsciousnessThreshold: ConfigValue[Double],
    vomitHungerPenalty: ConfigValue[Integer],
    vomitSaturationPenalty: ConfigValue[Double],
    vomitRelief: ConfigValue[Double],
    vomitReliefSpreadFraction: ConfigValue[Double]
)

private[config] object DiscomfortValues {

  def define(b: ModConfigSpec.Builder): DiscomfortValues = {
    b.push("discomfort")
    val s = DiscomfortValues(
      maxValue = b
        .comment("Maximum discomfort value.")
        .defineInRange("maxValue", 100.0, 1.0, 10000.0, classOf[Double]),
      distribution = b
        .comment(
          "Sampling distribution for each bite.",
          "Gaussian: normal distribution with possible tails.",
          "Uniform: even distribution across the configured interval."
        )
        .defineEnum("distribution", Distribution.Gaussian),
      spreadFraction = b
        .comment(
          "Spread of one dose as a fraction of its tier mean (gaussian standard deviation or",
          "uniform half-width), so every tier wobbles proportionally."
        )
        .defineInRange("spreadFraction", 0.25, 0.0, 1.0, classOf[Double]),
      level1Mean = b
        .comment(
          "Mean discomfort of tier-1 food (the casualtiesbelow:discomfort_1 tag: raw fish, honey",
          "by the bottle): a snackable nuisance — four or five in a row start to matter."
        )
        .defineInRange("level1Mean", 7.0, 0.0, 10000.0, classOf[Double]),
      level2Mean = b
        .comment(
          "Mean discomfort of tier-2 food (the casualtiesbelow:discomfort_2 tag: raw meat, dried",
          "kelp, chorus fruit): two start nausea, four hit refusal."
        )
        .defineInRange("level2Mean", 15.0, 0.0, 10000.0, classOf[Double]),
      level3Mean = b
        .comment(
          "Mean discomfort of tier-3 food (the casualtiesbelow:discomfort_3 tag: rotten,",
          "poisonous, not-human-food): one is felt, three induce vomiting."
        )
        .defineInRange("level3Mean", 30.0, 0.0, 10000.0, classOf[Double]),
      nauseaThreshold = b
        .comment("Discomfort at or above which the nausea screen effect is kept up.")
        .defineInRange("nauseaThreshold", 30.0, 0.0, 10000.0, classOf[Double]),
      refusalThreshold = b
        .comment(
          "Discomfort at or above which discomfort-bearing food can no longer be started —",
          "the character cannot bring themselves to swallow it."
        )
        .defineInRange("refusalThreshold", 60.0, 0.0, 10000.0, classOf[Double]),
      vomitChanceThreshold = b
        .comment(
          "Discomfort above which each server tick can trigger vomiting; the chance rises",
          "linearly from vomitMinChancePerTick here to vomitMaxChancePerTick at maxValue."
        )
        .defineInRange("vomitChanceThreshold", 30.0, 0.0, 10000.0, classOf[Double]),
      vomitMinChancePerTick = b
        .comment("Vomiting chance per tick immediately above vomitChanceThreshold (0.01 = 1%).")
        .defineInRange("vomitMinChancePerTick", 0.01, 0.0, 1.0, classOf[Double]),
      vomitMaxChancePerTick = b
        .comment("Vomiting chance per tick at maxValue (0.05 = 5%).")
        .defineInRange("vomitMaxChancePerTick", 0.05, 0.0, 1.0, classOf[Double]),
      decayRateLowPerSecond = b
        .comment(
          "Discomfort decay per second while below the nausea threshold: mild queasiness is",
          "tough to notice and fades on its own."
        )
        .defineInRange("decayRateLowPerSecond", 0.5, 0.0, 1000.0, classOf[Double]),
      decayRateHighPerSecond = b
        .comment(
          "Discomfort decay per second while at or above the nausea threshold: real sickness",
          "lingers and asks for active resolution."
        )
        .defineInRange("decayRateHighPerSecond", 0.2, 0.0, 1000.0, classOf[Double]),
      alreadyNauseousMultiplier = b
        .comment("Dose multiplier when eating while already at or above the nausea threshold.")
        .defineInRange("alreadyNauseousMultiplier", 1.25, 0.0, 100.0, classOf[Double]),
      overeatingMultiplier = b
        .comment("Dose multiplier when force-feeding on a full stomach (always-edible foods).")
        .defineInRange("overeatingMultiplier", 1.25, 0.0, 100.0, classOf[Double]),
      poorConditionMultiplier = b
        .comment(
          "Dose multiplier when eating while septic or barely conscious (below",
          "poorConditionConsciousnessThreshold)."
        )
        .defineInRange("poorConditionMultiplier", 1.5, 0.0, 100.0, classOf[Double]),
      poorConditionConsciousnessThreshold = b
        .comment("Consciousness below which the poor-condition dose multiplier applies.")
        .defineInRange("poorConditionConsciousnessThreshold", 50.0, 0.0, 100.0, classOf[Double]),
      vomitHungerPenalty = b
        .comment("Food level (0-20) lost when vomiting.")
        .defineInRange("vomitHungerPenalty", 6, 0, 20, classOf[Integer]),
      vomitSaturationPenalty = b
        .comment("Saturation lost when vomiting.")
        .defineInRange("vomitSaturationPenalty", 8.0, 0.0, 100.0, classOf[Double]),
      vomitRelief = b
        .comment("Mean discomfort removed by vomiting.")
        .defineInRange("vomitRelief", 30.0, 0.0, 10000.0, classOf[Double]),
      vomitReliefSpreadFraction = b
        .comment(
          "Uniform random spread around vomitRelief as a fraction of that value",
          "(0.05 = each vomit removes between 95% and 105% of the configured relief)."
        )
        .defineInRange("vomitReliefSpreadFraction", 0.05, 0.0, 1.0, classOf[Double])
    )
    b.pop()
    s
  }
}
