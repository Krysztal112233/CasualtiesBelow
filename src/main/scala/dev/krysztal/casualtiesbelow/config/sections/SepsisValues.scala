package dev.krysztal.casualtiesbelow.config.sections

import java.lang.Double

import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

private[config] final case class SepsisValues(
    maxSepsis: ConfigValue[Double],
    sepsisGainPerTick: ConfigValue[Double],
    sepsisDecayPerTick: ConfigValue[Double]
)

private[config] object SepsisValues {

  def define(b: ModConfigSpec.Builder): SepsisValues = {
    b.push("sepsis")
    val s = SepsisValues(
      maxSepsis = b
        .comment(
          "The maximum sepsis value; reaching it reduces the effective blood volume cap to zero."
        )
        .defineInRange("maxSepsis", 100.0, 1.0, 10000.0, classOf[Double]),
      sepsisGainPerTick = b
        .comment(
          "Sepsis gained per tick when the whole body is maximally infected (infection load 600),",
          "scaled linearly with the load fraction."
        )
        .defineInRange("sepsisGainPerTick", 0.05, 0.0, 10.0, classOf[Double]),
      sepsisDecayPerTick = b
        .comment(
          "Sepsis recovered per tick regardless of the infection load. With the defaults the",
          "break-even infection load is 360 out of 600: a single maxed-out limb infection cannot",
          "kill, but an infection spreading across the body does."
        )
        .defineInRange("sepsisDecayPerTick", 0.03, 0.0, 10.0, classOf[Double])
    )
    b.pop()
    s
  }
}
