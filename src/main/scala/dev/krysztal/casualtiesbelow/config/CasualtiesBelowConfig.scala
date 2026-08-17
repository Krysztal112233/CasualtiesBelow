package dev.krysztal.casualtiesbelow.config

import java.lang.Double

import dev.krysztal.casualtiesbelow.CasualtiesBelow

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

/** Common (server-authoritative) configuration, backed by Forge Config API Port. Values are written
  * to `config/casualtiesbelow-common.toml` and can be edited in-game via the ModMenu integration
  * provided by Forge Config API Port.
  */
object CasualtiesBelowConfig {
  private val Builder = new ModConfigSpec.Builder()

  object Vitals {
    Builder.push("vitals")
    val StartingHealth: ConfigValue[Double] = Builder
      .comment("Overall health a player starts with.")
      .gameRestart()
      .defineInRange("startingHealth", 100.0, 0.0, 100.0, classOf[Double])
    val StartingConsciousness: ConfigValue[Double] = Builder
      .comment("Consciousness a player starts with.")
      .gameRestart()
      .defineInRange("startingConsciousness", 100.0, 0.0, 100.0, classOf[Double])
    Builder.pop()
  }

  object Limbs {
    Builder.push("limbs")
    val StartingMuscleHealth: ConfigValue[Double] = Builder
      .comment("Muscle health each limb starts with.")
      .gameRestart()
      .defineInRange("startingMuscleHealth", 100.0, 0.0, 100.0, classOf[Double])
    val StartingSkinIntegrity: ConfigValue[Double] = Builder
      .comment("Skin integrity each limb starts with.")
      .gameRestart()
      .defineInRange("startingSkinIntegrity", 100.0, 0.0, 100.0, classOf[Double])
    Builder.pop()
  }

  object Movement {
    Builder.push("movement")
    val DislocationSpeedReduction: ConfigValue[Double] = Builder
      .comment(
        "Movement speed multiplier reduction per dislocated leg, as a fraction (e.g. 0.3 = 30% slower)."
      )
      .gameRestart()
      .defineInRange("dislocationSpeedReduction", 0.3, 0.0, 1.0, classOf[Double])
    val DislocationJumpReduction: ConfigValue[Double] = Builder
      .comment(
        "Jump strength multiplier reduction per dislocated leg, as a fraction (e.g. 0.5 = 50% lower jumps)."
      )
      .gameRestart()
      .defineInRange("dislocationJumpReduction", 0.5, 0.0, 1.0, classOf[Double])
    Builder.pop()
  }

  private lazy val Spec = {
    // Force initialization of the nested objects so their values are defined on the Builder
    // before the spec is built (nested objects are lazily initialized in Scala).
    Vitals
    Limbs
    Movement
    Builder.build()
  }

  def register(): Unit = {
    ConfigRegistry.INSTANCE.register(CasualtiesBelow.ModId, ModConfig.Type.COMMON, Spec)
  }
}
