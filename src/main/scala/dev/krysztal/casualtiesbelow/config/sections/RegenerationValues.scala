package dev.krysztal.casualtiesbelow.config.sections

import java.lang.Double

import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

/** Skin-regeneration tuning colocated so the base rate and its two situational floors (immune
  * health, dirtiness) are tuned together — they multiply into the same per-tick regrowth.
  */
private[config] final case class RegenerationValues(
    skinRestorePerTick: ConfigValue[Double],
    skinRegenMinImmuneMultiplier: ConfigValue[Double],
    skinRegenMinDirtinessMultiplier: ConfigValue[Double]
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
        .defineInRange("skinRestorePerTick", 0.001, 0.0, 10.0, classOf[Double]),
      skinRegenMinImmuneMultiplier = b
        .comment(
          "Skin regrowth multiplier at zero immune health, as a fraction of the base rate (0.25 =",
          "quarter speed); at full immune health skin regrows at the base rate. Never zero, so a",
          "dying player is not soft-locked out of healing."
        )
        .defineInRange("skinRegenMinImmuneMultiplier", 0.25, 0.0, 1.0, classOf[Double]),
      skinRegenMinDirtinessMultiplier = b
        .comment(
          "Skin-regrowth multiplier at maximum dirtiness, ramping linearly from 1 when",
          "clean. Never zero, and stacks with the immune multiplier."
        )
        .defineInRange("skinRegenMinDirtinessMultiplier", 0.75, 0.0, 1.0, classOf[Double])
    )
    b.pop()
    s
  }
}
