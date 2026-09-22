package dev.krysztal.casualtiesbelow.config.sections

import java.lang.Double
import java.lang.Integer

import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

private[config] final case class AdrenalineValues(
    maxValue: ConfigValue[Double],
    decayPerTick: ConfigValue[Double],
    combatGraceTicks: ConfigValue[Integer],
    shockProtectionPerPoint: ConfigValue[Double],
    painReductionPerPoint: ConfigValue[Double],
    maxPainReductionFraction: ConfigValue[Double]
)

private[config] object AdrenalineValues {

  def define(b: ModConfigSpec.Builder): AdrenalineValues = {
    b.push("adrenaline")
    val s = AdrenalineValues(
      maxValue = b
        .comment(
          "Maximum temporary adrenaline reserve. Set to zero to disable all adrenaline grants.",
          "Per-damage-source grant amounts are defined by adrenaline_rule datapack entries."
        )
        .defineInRange("maxValue", 100.0, 0.0, 10000.0, classOf[Double]),
      decayPerTick = b
        .comment(
          "Adrenaline removed per server tick after the combat grace window expires.",
          "The default 0.1 removes 2 points per second."
        )
        .defineInRange("decayPerTick", 0.1, 0.000001, 1000.0, classOf[Double]),
      combatGraceTicks = b
        .comment(
          "Ticks after the latest positive adrenaline stimulus before decay starts.",
          "Repeated accepted hits refresh this grace window; 100 ticks is 5 seconds."
        )
        .defineInRange("combatGraceTicks", 100, 0, 72000),
      shockProtectionPerPoint = b
        .comment(
          "Temporary pain-shock threshold added per adrenaline point.",
          "Effective collapse threshold = shockCollapseThreshold + adrenaline * this value.",
          "Set to zero to keep the reserve visible to APIs while disabling shock protection."
        )
        .defineInRange("shockProtectionPerPoint", 1.0, 0.0, 100.0, classOf[Double]),
      painReductionPerPoint = b
        .comment(
          "Fraction of acute injury pain prevented per adrenaline point.",
          "The default 0.005 means 10 reserve prevents 5% of pain from the same accepted hit."
        )
        .defineInRange("painReductionPerPoint", 0.005, 0.0, 1.0, classOf[Double]),
      maxPainReductionFraction = b
        .comment(
          "Maximum fraction of acute injury pain that adrenaline can prevent.",
          "This never changes tissue damage, bleeding, or condition onset."
        )
        .defineInRange("maxPainReductionFraction", 0.5, 0.0, 1.0, classOf[Double])
    )
    b.pop()
    s
  }
}
