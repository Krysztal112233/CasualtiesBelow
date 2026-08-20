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
  val MaxBloodVolume: ConfigValue[Double] = Builder
    .comment(
      "Total blood volume of a player, in mL; bleeding drains it and reaching zero is fatal."
    )
    .gameRestart()
    .defineInRange("maxBloodVolume", 5000.0, 100.0, 100000.0, classOf[Double])
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

  Builder.push("bleeding")
  val ClottingRatePerTick: ConfigValue[Double] = Builder
    .comment(
      "External bleeding clots linearly: this many mL/tick of bleeding rate are sealed per tick.",
      "At the default, a fresh sword cut (0.2 mL/tick) clots shut in 50 seconds."
    )
    .defineInRange("clottingRatePerTick", 0.0002, 0.0, 1.0, classOf[Double])
  val MaxExternalBleedingRate: ConfigValue[Double] = Builder
    .comment(
      "Upper bound of a limb's external bleeding rate (mL/tick) when its skin integrity is zero.",
      "The bound scales linearly with skin integrity: intact skin cannot bleed, half-intact skin",
      "bleeds at most half this rate."
    )
    .defineInRange("maxExternalBleedingRate", 1.0, 0.0, 100.0, classOf[Double])
  val BleedingRateJitter: ConfigValue[Double] = Builder
    .comment(
      "Random fluctuation of the bleeding rate granted by each wound, as a fraction of the rate",
      "(0.3 = rolled as rate × (1 ± 30%)); proportional, so larger wounds fluctuate more.",
      "0 disables fluctuation."
    )
    .defineInRange("bleedingRateJitter", 0.3, 0.0, 1.0, classOf[Double])
  Builder.pop()

  Builder.push("movement")
  val DislocationSpeedReduction: ConfigValue[Double] = Builder
    .comment(
      "Movement speed multiplier reduction per dislocated leg, as a fraction (e.g. 0.3 = 30% slower).",
      "A fractured leg counts as 1.5 dislocated legs."
    )
    .gameRestart()
    .defineInRange("dislocationSpeedReduction", 0.3, 0.0, 1.0, classOf[Double])
  val DislocationJumpReduction: ConfigValue[Double] = Builder
    .comment(
      "Jump strength multiplier reduction per dislocated leg, as a fraction (e.g. 0.5 = 50% lower jumps).",
      "A fractured leg counts as 1.5 dislocated legs."
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
  val PainDecayPerTick: ConfigValue[Double] = Builder
    .comment("Pain faded per tick on every limb (0.025 = a full limb's pain fades in ~200 s).")
    .defineInRange("painDecayPerTick", 0.025, 0.0, 10.0, classOf[Double])
  val FracturedWalkingPainPerTick: ConfigValue[Double] = Builder
    .comment(
      "Pain granted per tick while walking on a fractured leg, at full tissue damage (muscle and",
      "skin both at zero); scales linearly with the leg's average tissue damage."
    )
    .defineInRange("fracturedWalkingPainPerTick", 0.5, 0.0, 10.0, classOf[Double])
  val DislocatedWalkingPainPerTick: ConfigValue[Double] = Builder
    .comment(
      "Same as fracturedWalkingPainPerTick, but for walking on a dislocated leg."
    )
    .defineInRange("dislocatedWalkingPainPerTick", 0.3, 0.0, 10.0, classOf[Double])
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
