package dev.krysztal.casualtiesbelow.config.sections

import java.lang.Double

import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

private[config] final case class LimbsValues(
    startingMuscleHealth: ConfigValue[Double],
    startingSkinIntegrity: ConfigValue[Double]
)

private[config] object LimbsValues {

  def define(b: ModConfigSpec.Builder): LimbsValues = {
    b.push("limbs")
    val s = LimbsValues(
      startingMuscleHealth = b
        .comment("Muscle health each limb starts with.")
        .gameRestart()
        .defineInRange("startingMuscleHealth", 100.0, 0.0, 100.0, classOf[Double]),
      startingSkinIntegrity = b
        .comment("Skin integrity each limb starts with.")
        .gameRestart()
        .defineInRange("startingSkinIntegrity", 100.0, 0.0, 100.0, classOf[Double])
    )
    b.pop()
    s
  }
}
