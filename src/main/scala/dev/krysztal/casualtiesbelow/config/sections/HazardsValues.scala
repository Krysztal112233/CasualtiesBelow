package dev.krysztal.casualtiesbelow.config.sections

import java.lang.Double
import java.lang.Integer

import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

private[config] final case class HazardsValues(
    terminalHypoxiaDurationTicks: ConfigValue[Integer],
    inWallBloodOxygenDepletionPerTick: ConfigValue[Double],
    starvationBloodLossFractionPerDamage: ConfigValue[Double],
    easyStarvationBloodFloorFraction: ConfigValue[Double],
    normalStarvationBloodFloorFraction: ConfigValue[Double]
)

private[config] object HazardsValues {

  def define(b: ModConfigSpec.Builder): HazardsValues = {
    b.push("hazards")
    val s = HazardsValues(
      terminalHypoxiaDurationTicks = b
        .comment(
          "Ticks spent at zero blood oxygen while respiration remains failed before terminal hypoxia",
          "deals its fatal hit (20 ticks = 1 second). Must remain positive; ending the vanilla",
          "breathing block or opioid respiratory failure resets the persisted exposure timer."
        )
        .defineInRange("terminalHypoxiaDurationTicks", 160, 1, 72000),
      inWallBloodOxygenDepletionPerTick = b
        .comment(
          "Blood oxygen lost per tick while the player's head is in a wall. When exhausted-air",
          "drowning is active simultaneously, only the stronger of this and",
          "vitals.bloodOxygenDepletionPerTick applies."
        )
        .defineInRange("inWallBloodOxygenDepletionPerTick", 0.6, 0.0, 100.0, classOf[Double]),
      starvationBloodLossFractionPerDamage = b
        .comment(
          "Fraction of healthy max blood lost per accepted vanilla starvation damage point.",
          "The default 0.05 drains 250 mL per normal 1.0-damage pulse at 5000 mL healthy capacity."
        )
        .defineInRange("starvationBloodLossFractionPerDamage", 0.05, 0.0, 1.0, classOf[Double]),
      easyStarvationBloodFloorFraction = b
        .comment(
          "Easy starvation floor as a fraction of effective post-sepsis max blood. Vanilla hurt",
          "pulses stop at the floor; the default 0.5 leaves half of effective blood."
        )
        .defineInRange("easyStarvationBloodFloorFraction", 0.5, 0.0, 1.0, classOf[Double]),
      normalStarvationBloodFloorFraction = b
        .comment(
          "Normal starvation floor as a fraction of effective post-sepsis max blood. It is clamped",
          "no higher than the Easy floor at use time; the default 0.05 leaves five percent."
        )
        .defineInRange("normalStarvationBloodFloorFraction", 0.05, 0.0, 1.0, classOf[Double])
    )
    b.pop()
    s
  }
}
