package dev.krysztal.casualtiesbelow.config.sections

import java.lang.Double

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.physiology.pain.TotalPainStrategy

import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

private[config] final case class PainValues(
    totalPainStrategy: ModConfigSpec.EnumValue[TotalPainStrategy],
    totalPainDecay: ConfigValue[Double],
    totalPainFilterThreshold: ConfigValue[Double],
    painDecayPerTick: ConfigValue[Double],
    shockAccumulationStartPain: ConfigValue[Double],
    shockMaximumRatePain: ConfigValue[Double],
    shockMaximumGainPerTick: ConfigValue[Double],
    shockRecoveryPerTick: ConfigValue[Double],
    shockCollapseThreshold: ConfigValue[Double],
    shockWakeLoadCap: ConfigValue[Double],
    fracturedWalkingPainPerTick: ConfigValue[Double],
    dislocatedWalkingPainPerTick: ConfigValue[Double]
)

private[config] object PainValues {

  def define(b: ModConfigSpec.Builder): PainValues = {
    b.push("pain")
    val s = PainValues(
      totalPainStrategy = b
        .comment(
          "How per-limb pains are aggregated into whole-body pain.",
          "Max: only the worst injury counts. Sum: all pains add up.",
          "Geometric: descending-sorted pains weighted d^0, d^1, d^2, ... (see totalPainDecay)."
        )
        .defineEnum("totalPainStrategy", TotalPainStrategy.Geometric),
      totalPainDecay = b
        .comment(
          "Geometric strategy decay factor: limb pains sorted descending are weighted d^0, d^1, d^2, ...",
          "Lower values mean additional injuries beyond the worst count less."
        )
        .defineInRange("totalPainDecay", 0.3, 0.0, 1.0, classOf[Double]),
      totalPainFilterThreshold = b
        .comment(
          "Geometric strategy: limb pains below this value do not contribute (0 = no filtering).",
          "If every pain is filtered out, the worst single pain still counts."
        )
        .defineInRange("totalPainFilterThreshold", 0.0, 0.0, 100.0, classOf[Double]),
      painDecayPerTick = b
        .comment("Pain faded per tick on every limb (0.025 = a full limb's pain fades in ~200 s).")
        .defineInRange("painDecayPerTick", 0.025, 0.0, 10.0, classOf[Double]),
      shockAccumulationStartPain = b
        .comment(
          "Whole-body pain above which hidden pain-shock load starts accumulating.",
          "At or below this value the load instead recovers at shockRecoveryPerTick."
        )
        .defineInRange("shockAccumulationStartPain", 70.0, 0.0, 100.0, classOf[Double]),
      shockMaximumRatePain = b
        .comment(
          "Whole-body pain at which shock load reaches its maximum accumulation rate.",
          "Between the start and maximum-rate thresholds the rate scales linearly; an effective",
          "value below shockAccumulationStartPain is treated as equal to it."
        )
        .defineInRange("shockMaximumRatePain", 80.0, 0.0, 100.0, classOf[Double]),
      shockMaximumGainPerTick = b
        .comment(
          "Maximum hidden shock load gained per tick at or above shockMaximumRatePain.",
          "The default 0.2 is 4 load per second, so 0 to the default collapse threshold takes 22.5 s."
        )
        .defineInRange("shockMaximumGainPerTick", 0.2, 0.0, 100.0, classOf[Double]),
      shockRecoveryPerTick = b
        .comment(
          "Shock load removed per tick while whole-body pain is at or below the accumulation start.",
          "The default 0.1 removes 2 load per second."
        )
        .defineInRange("shockRecoveryPerTick", 0.1, 0.0, 100.0, classOf[Double]),
      shockCollapseThreshold = b
        .comment(
          "Shock load that collapses a stable player when crossed upward and permits consciousness",
          "recovery when crossed downward. Load can continue accumulating to 100 after collapse."
        )
        .defineInRange(
          "shockCollapseThreshold",
          90.0,
          VitalsComponent.MinimumWakeThreshold,
          VitalsComponent.MaxValue,
          classOf[Double]
        ),
      shockWakeLoadCap = b
        .comment(
          "Maximum hidden shock load retained when a recovering player wakes.",
          "The default zero fully clears residual load, giving every new episode the full 22.5-second",
          "accumulation window at the maximum gain rate."
        )
        .defineInRange("shockWakeLoadCap", 0.0, 0.0, 100.0, classOf[Double]),
      fracturedWalkingPainPerTick = b
        .comment(
          "Pain granted per tick while walking on a fractured leg, at full tissue damage (muscle and",
          "skin both at zero); scales linearly with the leg's average tissue damage."
        )
        .defineInRange("fracturedWalkingPainPerTick", 0.5, 0.0, 10.0, classOf[Double]),
      dislocatedWalkingPainPerTick = b
        .comment(
          "Same as fracturedWalkingPainPerTick, but for walking on a dislocated leg."
        )
        .defineInRange("dislocatedWalkingPainPerTick", 0.3, 0.0, 10.0, classOf[Double])
    )
    b.pop()
    s
  }
}
