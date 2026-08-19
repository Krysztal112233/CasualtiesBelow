package dev.krysztal.casualtiesbelow.config

import java.lang.Boolean
import java.lang.Double

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.pain.TotalPainStrategy

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
    .defineInRange("dislocationJumpReduction", 0.2, 0.0, 1.0, classOf[Double])
  Builder.pop()

  Builder.push("pain")
  val PainStrategy: ModConfigSpec.EnumValue[TotalPainStrategy] = Builder
    .comment(
      "How per-limb pains are aggregated into whole-body pain.",
      "Max: only the worst injury counts. Sum: all pains add up.",
      "Geometric: descending-sorted pains weighted d^0, d^1, d^2, ... (see totalPainDecay)."
    )
    .defineEnum("totalPainStrategy", TotalPainStrategy.Geometric)
  val TotalPainDecay: ConfigValue[Double] = Builder
    .comment(
      "Geometric strategy decay factor: limb pains sorted descending are weighted d^0, d^1, d^2, ...",
      "Lower values mean additional injuries beyond the worst count less."
    )
    .defineInRange("totalPainDecay", 0.3, 0.0, 1.0, classOf[Double])
  val TotalPainFilterThreshold: ConfigValue[Double] = Builder
    .comment(
      "Geometric strategy: limb pains below this value do not contribute (0 = no filtering).",
      "If every pain is filtered out, the worst single pain still counts."
    )
    .defineInRange("totalPainFilterThreshold", 0.0, 0.0, 100.0, classOf[Double])
  Builder.pop()

  Builder.push("fall")
  val FallDamageFormula: FormulaConfigValue = new FormulaConfigValue(
    Builder,
    "damageFormula",
    "max(0, distance - safeDistance)^1.5 * 0.5 * modifier * multiplier",
    List("distance", "safeDistance", "modifier", "multiplier"),
    comment = Seq(
      "Fall damage formula, compiled with EvalEx (https://github.com/ezylang/EvalEx).",
      "Available variables: distance (fall distance), safeDistance (safe fall distance attribute),",
      "modifier (vanilla damage modifier), multiplier (fall damage multiplier attribute).",
      "Invalid formulas are rejected and corrected to the default. Hot-reloaded on file change."
    )
  )
  val AffectAllLivingEntities: ConfigValue[Boolean] = Builder
    .comment(
      "Whether the custom fall damage formula applies to all living entities, not just players."
    )
    .gameRestart()
    .define("affectAllLivingEntities", false)
  Builder.pop()

  private val Spec = Builder.build()

  def register(): Unit =
    ConfigRegistry.INSTANCE.register(CasualtiesBelow.ModId, ModConfig.Type.COMMON, Spec)
}
