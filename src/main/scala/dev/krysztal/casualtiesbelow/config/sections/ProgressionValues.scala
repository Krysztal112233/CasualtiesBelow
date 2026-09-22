package dev.krysztal.casualtiesbelow.config.sections

import java.lang.Double

import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

private[config] final case class ProgressionValues(
    notTodayNearMaxBleedingFraction: ConfigValue[Double]
)

private[config] object ProgressionValues {

  def define(b: ModConfigSpec.Builder): ProgressionValues = {
    b.push("progression")
    val s = ProgressionValues(
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
