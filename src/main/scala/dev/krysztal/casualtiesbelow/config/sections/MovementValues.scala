package dev.krysztal.casualtiesbelow.config.sections

import java.lang.Double

import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

private[config] final case class MovementValues(
    dislocationSpeedReduction: ConfigValue[Double],
    dislocationJumpReduction: ConfigValue[Double],
    muscleSpeedReduction: ConfigValue[Double],
    muscleJumpReduction: ConfigValue[Double]
)

private[config] object MovementValues {

  def define(b: ModConfigSpec.Builder): MovementValues = {
    b.push("movement")
    val s = MovementValues(
      dislocationSpeedReduction = b
        .comment(
          "Movement speed multiplier reduction per dislocated leg, as a fraction (e.g. 0.3 = 30% slower).",
          "A fractured leg counts as 1.5 dislocated legs."
        )
        .gameRestart()
        .defineInRange("dislocationSpeedReduction", 0.3, 0.0, 1.0, classOf[Double]),
      dislocationJumpReduction = b
        .comment(
          "Jump strength multiplier reduction per dislocated leg, as a fraction (e.g. 0.5 = 50% lower jumps).",
          "A fractured leg counts as 1.5 dislocated legs."
        )
        .gameRestart()
        .defineInRange("dislocationJumpReduction", 0.2, 0.0, 1.0, classOf[Double]),
      muscleSpeedReduction = b
        .comment(
          "Maximum movement speed multiplier reduction from leg muscle damage, reached when both",
          "legs have zero muscle health. Each leg's normalized deficit is squared, then both legs",
          "are averaged; this layer multiplies independently with fracture and dislocation penalties."
        )
        .gameRestart()
        .defineInRange("muscleSpeedReduction", 0.75, 0.0, 1.0, classOf[Double]),
      muscleJumpReduction = b
        .comment(
          "Maximum jump strength multiplier reduction from leg muscle damage, reached when both",
          "legs have zero muscle health. Uses the same squared-deficit average as movement speed."
        )
        .gameRestart()
        .defineInRange("muscleJumpReduction", 0.6, 0.0, 1.0, classOf[Double])
    )
    b.pop()
    s
  }
}
