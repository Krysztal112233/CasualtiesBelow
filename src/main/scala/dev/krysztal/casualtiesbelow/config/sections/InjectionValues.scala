package dev.krysztal.casualtiesbelow.config.sections

import java.lang.Double
import java.lang.Integer

import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

private[config] final case class InjectionValues(
    maxSpeedFractionPerSecond: ConfigValue[Double],
    fullDoseSideEffectDiscomfort: ConfigValue[Double],
    fullDoseSideEffectPain: ConfigValue[Double],
    fullSpeedPressDepthPixels: ConfigValue[Integer],
    batchIntervalMilliseconds: ConfigValue[Integer]
)

private[config] object InjectionValues {

  def define(b: ModConfigSpec.Builder): InjectionValues = {
    b.push("injection")
    val s = InjectionValues(
      maxSpeedFractionPerSecond = b
        .comment(
          "Fraction of a syringe pushed per second at full plunger press in the injection screen.",
          "Applies to every injectable; dose always lands proportionally to the pushed amount."
        )
        .defineInRange("maxSpeedFractionPerSecond", 0.5, 0.01, 10.0, classOf[Double]),
      fullDoseSideEffectDiscomfort = b
        .comment(
          "Discomfort gained when one full syringe is pushed at maximum speed.",
          "Scales linearly with push speed and pushed amount."
        )
        .defineInRange("fullDoseSideEffectDiscomfort", 10.0, 0.0, 100.0, classOf[Double]),
      fullDoseSideEffectPain = b
        .comment(
          "Injection-site pain gained when one full syringe is pushed at maximum speed.",
          "Scales linearly with push speed and pushed amount."
        )
        .defineInRange("fullDoseSideEffectPain", 10.0, 0.0, 100.0, classOf[Double]),
      fullSpeedPressDepthPixels = b
        .comment(
          "Screen pixels of plunger press depth (cursor below the thumb pad) that request maximum",
          "injection speed."
        )
        .defineInRange("fullSpeedPressDepthPixels", 60, 10, 500),
      batchIntervalMilliseconds = b
        .comment("Milliseconds between injection progress batches sent to the server.")
        .defineInRange("batchIntervalMilliseconds", 200, 20, 5000)
    )
    b.pop()
    s
  }
}
