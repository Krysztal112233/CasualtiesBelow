package dev.krysztal.casualtiesbelow.config.sections

import java.lang.Double
import java.lang.Integer

import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

private[config] final case class BleedingValues(
    clottingRatePerTick: ConfigValue[Double],
    maxExternalBleedingRate: ConfigValue[Double],
    totemBloodRestoreFraction: ConfigValue[Double],
    totemHemostasisInitialReduction: ConfigValue[Double],
    totemHemostasisDurationTicks: ConfigValue[Integer],
    notTodayNearMaxBleedingFraction: ConfigValue[Double]
)

private[config] object BleedingValues {

  def define(b: ModConfigSpec.Builder): BleedingValues = {
    b.push("bleeding")
    val s = BleedingValues(
      clottingRatePerTick = b
        .comment(
          "External bleeding clots linearly: this many mL/tick of bleeding rate are sealed per tick.",
          "At the default, a fresh sword cut (0.2 mL/tick) clots shut in 4 minutes 10 seconds;",
          "a limb at the maximum 1.0 mL/tick takes 20 minutes 50 seconds."
        )
        .defineInRange("clottingRatePerTick", 0.00004, 0.0, 1.0, classOf[Double]),
      maxExternalBleedingRate = b
        .comment(
          "Upper bound of a limb's external bleeding rate (mL/tick) when its skin integrity is zero.",
          "The bound scales linearly with skin integrity: intact skin cannot bleed, half-intact skin",
          "bleeds at most half this rate."
        )
        .defineInRange("maxExternalBleedingRate", 1.0, 0.0, 100.0, classOf[Double]),
      totemBloodRestoreFraction = b
        .comment(
          "Fraction of the effective maximum blood volume restored when death protection saves",
          "a player from blood loss. It must stay positive to avoid a zero-blood rescue loop; wounds",
          "remain open and keep bleeding."
        )
        .defineInRange("totemBloodRestoreFraction", 0.2, 0.000001, 1.0, classOf[Double]),
      totemHemostasisInitialReduction = b
        .comment(
          "Initial fraction of actual blood drain prevented after a blood-loss totem rescue.",
          "The reduction decays linearly to zero over totemHemostasisDurationTicks."
        )
        .defineInRange("totemHemostasisInitialReduction", 0.8, 0.0, 1.0, classOf[Double]),
      totemHemostasisDurationTicks = b
        .comment(
          "Duration of the post-totem hemostasis window in ticks (20 ticks = 1 second).",
          "The hidden timer freezes with the rest of physiology in creative and spectator modes."
        )
        .defineInRange("totemHemostasisDurationTicks", 600, 0, 72000),
      notTodayNearMaxBleedingFraction = b
        .comment(
          "Near-maximum hemorrhage threshold of the \"Not Today\" advancement, as a fraction of a",
          "limb's maximum external bleeding rate (maxExternalBleedingRate). An episode arms once the",
          "player's total external bleeding rate reaches this threshold, and completes when external",
          "bleeding is fully stopped while the player lives to see it."
        )
        .defineInRange("notTodayNearMaxBleedingFraction", 0.75, 0.0, 1.0, classOf[Double])
    )
    b.pop()
    s
  }
}
