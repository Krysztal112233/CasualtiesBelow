package dev.krysztal.casualtiesbelow.config.sections

import java.lang.Double

import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

private[config] final case class RegenerationValues(
    skinRestorePerTick: ConfigValue[Double]
)

private[config] object RegenerationValues {

  def define(b: ModConfigSpec.Builder): RegenerationValues = {
    b.push("regeneration")
    val s = RegenerationValues(
      skinRestorePerTick = b
        .comment(
          "Skin integrity restored per tick on every damaged limb while vanilla Regeneration is",
          "active, before linear effect-level scaling (Regeneration I = 1×, Regeneration II = 2×).",
          "Recovery continues while bleeding and immediately tightens the bleeding cap; 0 disables it."
        )
        .defineInRange("skinRestorePerTick", 0.001, 0.0, 10.0, classOf[Double])
    )
    b.pop()
    s
  }
}
