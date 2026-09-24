package dev.krysztal.casualtiesbelow.config.sections

import java.lang.Double

import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

/** Shared randomness knobs. Every mechanic used to carry its own proportional jitter/spread entry;
  * they were the same 0..1 "how wobbly is this roll" parameter in every domain, so the overall
  * random feel is tuned here instead of per system.
  */
private[config] final case class RandomnessValues(
    worldPulseJitter: ConfigValue[Double],
    doseSpreadFraction: ConfigValue[Double]
)

private[config] object RandomnessValues {

  def define(b: ModConfigSpec.Builder): RandomnessValues = {
    b.push("randomness")
    val s = RandomnessValues(
      worldPulseJitter = b
        .comment(
          "Random fluctuation of world event pulses (dirtiness pulses, zombie-hit immune drain,",
          "bleeding granted by a wound), as a fraction of the pulse: 0.3 = rolled as value ×",
          "(1 ± 30%). Proportional, so larger pulses fluctuate more. 0 disables fluctuation."
        )
        .defineInRange("worldPulseJitter", 0.3, 0.0, 1.0, classOf[Double]),
      doseSpreadFraction = b
        .comment(
          "Spread of sampled doses (discomfort per bite, vomit relief, food immune effect) as a",
          "fraction of the dose mean: gaussian standard deviation or uniform half-width, so every",
          "dose wobbles proportionally. Merged from per-system spreads (0.25 discomfort, 0.05",
          "vomit relief, 0.2 food immune) into one compromise; 0 disables dose randomness."
        )
        .defineInRange("doseSpreadFraction", 0.2, 0.0, 1.0, classOf[Double])
    )
    b.pop()
    s
  }
}
