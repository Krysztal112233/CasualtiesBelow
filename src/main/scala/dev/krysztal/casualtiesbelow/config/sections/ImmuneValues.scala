package dev.krysztal.casualtiesbelow.config.sections

import java.lang.Double
import java.lang.Integer

import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

private[config] final case class ImmuneValues(
    fedImmuneRegenPerTick: ConfigValue[Double],
    hungryImmuneDrainPerTick: ConfigValue[Double],
    fedFoodLevelThreshold: ConfigValue[Integer],
    hungryFoodLevelThreshold: ConfigValue[Integer],
    zombieHitImmuneDrain: ConfigValue[Double],
    poisonImmuneDrainPerTick: ConfigValue[Double]
)

private[config] object ImmuneValues {

  def define(b: ModConfigSpec.Builder): ImmuneValues = {
    b.push("immune")
    val s = ImmuneValues(
      fedImmuneRegenPerTick = b
        .comment(
          "Immune health regained per tick while awake and fed (food level at or above",
          "fedFoodLevelThreshold)."
        )
        .defineInRange("fedImmuneRegenPerTick", 0.005, 0.0, 10.0, classOf[Double]),
      hungryImmuneDrainPerTick = b
        .comment(
          "Immune health lost per tick while hungry (food level below hungryFoodLevelThreshold)."
        )
        .defineInRange("hungryImmuneDrainPerTick", 0.01, 0.0, 10.0, classOf[Double]),
      fedFoodLevelThreshold = b
        .comment(
          "Food level (0-20) at or above which immune health regenerates while awake; 18 matches",
          "vanilla's natural-regeneration threshold."
        )
        .defineInRange("fedFoodLevelThreshold", 18, 0, 20, classOf[Integer]),
      hungryFoodLevelThreshold = b
        .comment(
          "Food level (0-20) below which immune health drains; 7 matches vanilla's sprinting",
          "cutoff (vanilla requires food > 6 to sprint, so at 6 the player is already exhausted)."
        )
        .defineInRange("hungryFoodLevelThreshold", 7, 0, 20, classOf[Integer]),
      zombieHitImmuneDrain = b
        .comment(
          "Immune health lost per zombie-family hit (entity type tag minecraft:zombies), rolled",
          "with randomness.worldPulseJitter fluctuation. One-way feedback: only external attacks",
          "drain immune health; infections never do."
        )
        .defineInRange("zombieHitImmuneDrain", 5.0, 0.0, 1000.0, classOf[Double]),
      poisonImmuneDrainPerTick = b
        .comment(
          "Immune health lost per tick while vanilla Poison is active, before linear effect-level",
          "scaling (Poison I = 1×, Poison II = 2×). This combines additively with diet; 0",
          "disables poison immune drain."
        )
        .defineInRange("poisonImmuneDrainPerTick", 0.05, 0.0, 10.0, classOf[Double])
    )
    b.pop()
    s
  }
}
