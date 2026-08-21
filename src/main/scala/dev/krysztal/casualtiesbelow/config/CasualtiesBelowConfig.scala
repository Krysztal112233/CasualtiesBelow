package dev.krysztal.casualtiesbelow.config

import java.lang.Boolean
import java.lang.Double
import java.lang.Integer

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
  val MaxImmuneHealth: ConfigValue[Double] = Builder
    .comment(
      "Maximum (and starting) immune health. With the default infection rates, the break-even",
      "point where the immune system exactly matches infection spread is 120 out of 200."
    )
    .gameRestart()
    .defineInRange("maxImmuneHealth", 200.0, 1.0, 10000.0, classOf[Double])
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

  Builder.push("infection")
  val InfectionChancePerTick: ConfigValue[Double] = Builder
    .comment(
      "Per-tick probability that a wound starts an infection, at zero skin integrity; scales",
      "linearly with the skin damage (half-intact skin: half the chance). Only wounds below",
      "the skin integrity threshold (20 damage, i.e. under 80) can get infected at all.",
      "0 disables infections."
    )
    .defineInRange("infectionChancePerTick", 0.0005, 0.0, 1.0, classOf[Double])
  val InfectionSpreadPerTick: ConfigValue[Double] = Builder
    .comment(
      "Infection progress gained per tick at zero immune health; scales down linearly and",
      "reaches zero at full immune health. 0.03 = an unchecked infection runs 0 to 100 in",
      "~2.8 min."
    )
    .defineInRange("infectionSpreadPerTick", 0.03, 0.0, 10.0, classOf[Double])
  val InfectionFightPerTick: ConfigValue[Double] = Builder
    .comment(
      "Infection progress removed per tick at full immune health; scales down linearly and",
      "reaches zero at zero immune health. With the defaults (spread 0.03, fight 0.02, max",
      "immune health 200), the break-even immune health is 120: above it infections recede,",
      "below it they spread."
    )
    .defineInRange("infectionFightPerTick", 0.02, 0.0, 10.0, classOf[Double])
  val SkinRegenMinImmuneMultiplier: ConfigValue[Double] = Builder
    .comment(
      "Skin regrowth multiplier at zero immune health, as a fraction of the base rate (0.25 =",
      "quarter speed); at full immune health skin regrows at the base rate. Never zero, so a",
      "dying player is not soft-locked out of healing."
    )
    .defineInRange("skinRegenMinImmuneMultiplier", 0.25, 0.0, 1.0, classOf[Double])
  val InfectionEffectStartProgress: ConfigValue[Double] = Builder
    .comment(
      "Infection progress at which local consequences (pain, muscle decay) begin; below this",
      "an infection is asymptomatic."
    )
    .defineInRange("infectionEffectStartProgress", 20.0, 0.0, 100.0, classOf[Double])
  val InfectionEffectFullProgress: ConfigValue[Double] = Builder
    .comment(
      "Infection progress at which local consequences reach full strength; between the start",
      "and this value the effect strength ramps up linearly."
    )
    .defineInRange("infectionEffectFullProgress", 40.0, 0.0, 100.0, classOf[Double])
  val InfectionPainPerTick: ConfigValue[Double] = Builder
    .comment(
      "Pain granted per tick by an infection at full effect strength (see",
      "infectionEffectFullProgress). At the default, a full-strength infection outruns natural",
      "pain decay, so the limb keeps hurting until the infection recedes."
    )
    .defineInRange("infectionPainPerTick", 0.05, 0.0, 10.0, classOf[Double])
  val InfectionMuscleDecayPerTick: ConfigValue[Double] = Builder
    .comment(
      "Muscle health destroyed per tick by an infection at full effect strength (see",
      "infectionEffectFullProgress). At the default (double the muscle regrowth rate), a",
      "full-strength infection wastes the limb away in about half an hour."
    )
    .defineInRange("infectionMuscleDecayPerTick", 0.005, 0.0, 10.0, classOf[Double])
  Builder.pop()

  Builder.push("immune")
  val FedImmuneRegenPerTick: ConfigValue[Double] = Builder
    .comment(
      "Immune health regained per tick while awake and fed (food level at or above",
      "fedFoodLevelThreshold)."
    )
    .defineInRange("fedImmuneRegenPerTick", 0.005, 0.0, 10.0, classOf[Double])
  val HungryImmuneDrainPerTick: ConfigValue[Double] = Builder
    .comment(
      "Immune health lost per tick while hungry (food level below hungryFoodLevelThreshold)."
    )
    .defineInRange("hungryImmuneDrainPerTick", 0.01, 0.0, 10.0, classOf[Double])
  val FedFoodLevelThreshold: ConfigValue[Integer] = Builder
    .comment(
      "Food level (0-20) at or above which immune health regenerates while awake; 18 matches",
      "vanilla's natural-regeneration threshold."
    )
    .defineInRange("fedFoodLevelThreshold", 18, 0, 20, classOf[Integer])
  val HungryFoodLevelThreshold: ConfigValue[Integer] = Builder
    .comment(
      "Food level (0-20) below which immune health drains; 7 matches vanilla's sprinting",
      "cutoff (vanilla requires food > 6 to sprint, so at 6 the player is already exhausted)."
    )
    .defineInRange("hungryFoodLevelThreshold", 7, 0, 20, classOf[Integer])
  val ZombieHitImmuneDrain: ConfigValue[Double] = Builder
    .comment(
      "Immune health lost per zombie-family hit (entity type tag minecraft:zombies), rolled",
      "with zombieHitImmuneDrainJitter fluctuation. One-way feedback: only external attacks",
      "drain immune health; infections never do."
    )
    .defineInRange("zombieHitImmuneDrain", 5.0, 0.0, 1000.0, classOf[Double])
  val ZombieHitImmuneDrainJitter: ConfigValue[Double] = Builder
    .comment(
      "Random fluctuation of the per-hit immune drain, as a fraction of the drain (0.3 =",
      "rolled as drain × (1 ± 30%)); proportional, so a larger drain fluctuates more. 0",
      "disables fluctuation."
    )
    .defineInRange("zombieHitImmuneDrainJitter", 0.3, 0.0, 1.0, classOf[Double])
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

  /** Immune health at which infection spread and immune fight exactly cancel out:
    * `max × spread / (spread + fight)`. Below it infections spread, above it they recede.
    */
  def immuneBreakEven: Double = {
    val spread = InfectionSpreadPerTick.get()
    val fight = InfectionFightPerTick.get()
    if (spread + fight <= 0.0) {
      0.0
    } else {
      MaxImmuneHealth.get() * spread / (spread + fight)
    }
  }

  def register(): Unit =
    ConfigRegistry.INSTANCE.register(CasualtiesBelow.ModId, ModConfig.Type.COMMON, Spec)
}
