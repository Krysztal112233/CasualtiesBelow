package dev.krysztal.casualtiesbelow.config

import java.lang.Double

import dev.krysztal.casualtiesbelow.CasualtiesBelow

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.ModConfigSpec

/** Common (server-authoritative) configuration, backed by Forge Config API Port. Values are written
  * to `config/casualtiesbelow-common.toml` and can be edited in-game via the ModMenu integration
  * provided by Forge Config API Port.
  */
object CasualtiesBelowConfig {
  private val Builder = new ModConfigSpec.Builder()

  {
    Builder.push("vitals")
    val StartingHealth: ModConfigSpec.ConfigValue[Double] = Builder
      .comment("Overall health a player starts with.")
      .defineInRange("startingHealth", 100.0, 0.0, 100.0, classOf[Double])
    val StartingConsciousness: ModConfigSpec.ConfigValue[Double] = Builder
      .comment("Consciousness a player starts with.")
      .defineInRange("startingConsciousness", 100.0, 0.0, 100.0, classOf[Double])
    Builder.pop()
  }

  {
    Builder.push("limbs")
    val StartingMuscleHealth: ModConfigSpec.ConfigValue[Double] = Builder
      .comment("Muscle health each limb starts with.")
      .defineInRange("startingMuscleHealth", 100.0, 0.0, 100.0, classOf[Double])
    val StartingSkinIntegrity: ModConfigSpec.ConfigValue[Double] = Builder
      .comment("Skin integrity each limb starts with.")
      .defineInRange("startingSkinIntegrity", 100.0, 0.0, 100.0, classOf[Double])
    Builder.pop()
  }

  private val Spec = Builder.build()

  def register(): Unit = {
    ConfigRegistry.INSTANCE.register(CasualtiesBelow.ModId, ModConfig.Type.COMMON, Spec)
  }
}
