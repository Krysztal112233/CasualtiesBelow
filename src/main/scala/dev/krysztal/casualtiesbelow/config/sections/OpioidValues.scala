package dev.krysztal.casualtiesbelow.config.sections

import java.lang.Double

import dev.krysztal.casualtiesbelow.config.FormulaConfigValue

import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

private[config] final case class OpioidValues(
    levelDecayPerTick: ConfigValue[Double],
    dependenceExposurePerLevelPerTick: ConfigValue[Double],
    dependenceDecayPerTick: ConfigValue[Double],
    opioidAnalgesiaFormula: FormulaConfigValue,
    excitementStartLevel: ConfigValue[Double],
    excitementEndLevel: ConfigValue[Double],
    opioidSedationCeilingFormula: FormulaConfigValue,
    opioidRespiratoryEfficiencyFormula: FormulaConfigValue,
    respiratoryFailureEfficiencyThreshold: ConfigValue[Double],
    respiratoryFailureOxygenDrainPerTick: ConfigValue[Double],
    withdrawalDependenceThreshold: ConfigValue[Double],
    withdrawalLevelPerDependence: ConfigValue[Double],
    withdrawalPainMultiplier: ConfigValue[Double],
    withdrawalDiscomfortPerTick: ConfigValue[Double],
    withdrawalDiscomfortTarget: ConfigValue[Double],
    refinedSyringeDose: ConfigValue[Double],
    crudeSyringeDoseMean: ConfigValue[Double],
    crudeSyringeDoseSigma: ConfigValue[Double],
    crudeSyringeDoseMinimum: ConfigValue[Double],
    crudeSyringeDoseMaximum: ConfigValue[Double],
    unmarkedSyringeJitterFraction: ConfigValue[Double]
)

private[config] object OpioidValues {

  def define(b: ModConfigSpec.Builder): OpioidValues = {
    b.push("opioid")
    val s = OpioidValues(
      levelDecayPerTick = b
        .comment(
          "Acute opioid level removed per server tick. The default drains a full 200-point level",
          "in about 20 minutes (20 ticks = 1 second)."
        )
        .defineInRange("levelDecayPerTick", 0.0083, 0.0, 200.0, classOf[Double]),
      dependenceExposurePerLevelPerTick = b
        .comment(
          "Dependence gained per stored opioid-level point per tick.",
          "The default makes one isolated 100-point exposure contribute approximately 7.5 dependence."
        )
        .defineInRange("dependenceExposurePerLevelPerTick", 0.0000125, 0.0, 1.0, classOf[Double]),
      dependenceDecayPerTick = b
        .comment(
          "Dependence removed per tick, including while acute opioid exposure adds dependence.",
          "The default is 3 points per Minecraft day (24000 ticks)."
        )
        .defineInRange("dependenceDecayPerTick", 0.000125, 0.0, 100.0, classOf[Double]),
      opioidAnalgesiaFormula = new FormulaConfigValue(
        b,
        "analgesiaFormula",
        "min(0.85, level / 150) / (1 + dependence / 100)",
        List("level", "dependence"),
        comment = Seq(
          "Fraction of aggregated pain masked before pain-shock progression.",
          "Available variables: level (0-200), dependence (0-100). Limb pain storage and medical",
          "display values remain unchanged. Invalid formulas fall back to the default."
        )
      ),
      excitementStartLevel = b
        .comment(
          "Reserved excitement-band lower bound. Phase 1 assigns no mechanical effect to it."
        )
        .defineInRange("excitementStartLevel", 50.0, 0.0, 200.0, classOf[Double]),
      excitementEndLevel = b
        .comment(
          "Reserved excitement-band upper bound. Phase 1 assigns no mechanical effect to it."
        )
        .defineInRange("excitementEndLevel", 120.0, 0.0, 200.0, classOf[Double]),
      opioidSedationCeilingFormula = new FormulaConfigValue(
        b,
        "sedationCeilingFormula",
        "100 - 0.5 * max(0, level - 110)",
        List("level"),
        comment = Seq(
          "Opioid-derived consciousness ceiling.",
          "Available variable: level (0-200). The result is clamped to the consciousness range."
        )
      ),
      opioidRespiratoryEfficiencyFormula = new FormulaConfigValue(
        b,
        "respiratoryEfficiencyFormula",
        "1 - 0.9 * max(0, level - (120 + min(20, dependence * 0.2))) / 80",
        List("level", "dependence"),
        comment = Seq(
          "Respiratory efficiency used to scale oxygen recovery.",
          "Available variables: level (0-200), dependence (0-100). Dependence moves onset right by",
          "0.2 level per point, capped at 20. The result is clamped to 0-1."
        )
      ),
      respiratoryFailureEfficiencyThreshold = b
        .comment(
          "Respiratory efficiency below which oxygen drains even with vanilla air available.",
          "The comparison is strict: efficiency equal to this threshold is not respiratory failure."
        )
        .defineInRange("respiratoryFailureEfficiencyThreshold", 0.3, 0.0, 1.0, classOf[Double]),
      respiratoryFailureOxygenDrainPerTick = b
        .comment("Blood oxygen drained per tick during opioid respiratory failure.")
        .defineInRange("respiratoryFailureOxygenDrainPerTick", 0.3, 0.0, 100.0, classOf[Double]),
      withdrawalDependenceThreshold = b
        .comment("Dependence must be strictly above this value for withdrawal to become active.")
        .defineInRange("withdrawalDependenceThreshold", 20.0, 0.0, 100.0, classOf[Double]),
      withdrawalLevelPerDependence = b
        .comment("Withdrawal is active while opioid level is below dependence times this factor.")
        .defineInRange("withdrawalLevelPerDependence", 0.6, 0.0, 2.0, classOf[Double]),
      withdrawalPainMultiplier = b
        .comment("Multiplier applied to acute injury and walking-strain pain during withdrawal.")
        .defineInRange("withdrawalPainMultiplier", 1.25, 0.0, 10.0, classOf[Double]),
      withdrawalDiscomfortPerTick = b
        .comment("Discomfort gained per tick during withdrawal (0.0025 = 0.05 per second).")
        .defineInRange("withdrawalDiscomfortPerTick", 0.0025, 0.0, 100.0, classOf[Double]),
      withdrawalDiscomfortTarget = b
        .comment("Withdrawal discomfort climbs toward, but never beyond, this value.")
        .defineInRange("withdrawalDiscomfortTarget", 35.0, 0.0, 100.0, classOf[Double]),
      refinedSyringeDose = b
        .comment("Base opioid dose drawn from one refined poppy ampoule.")
        .defineInRange("refinedSyringeDose", 50.0, 0.0, 200.0, classOf[Double]),
      crudeSyringeDoseMean = b
        .comment("Mean opioid dose sampled when drawing directly from crude poppy liquid.")
        .defineInRange("crudeSyringeDoseMean", 40.0, 0.0, 200.0, classOf[Double]),
      crudeSyringeDoseSigma = b
        .comment("Standard deviation of the normal crude-poppy dose sample.")
        .defineInRange("crudeSyringeDoseSigma", 13.0, 0.0, 200.0, classOf[Double]),
      crudeSyringeDoseMinimum = b
        .comment("Minimum crude-poppy base dose after normal sampling.")
        .defineInRange("crudeSyringeDoseMinimum", 20.0, 0.0, 200.0, classOf[Double]),
      crudeSyringeDoseMaximum = b
        .comment("Maximum crude-poppy base dose after normal sampling.")
        .defineInRange("crudeSyringeDoseMaximum", 60.0, 0.0, 200.0, classOf[Double]),
      unmarkedSyringeJitterFraction = b
        .comment("Maximum uniform measurement error fraction applied by an unmarked syringe.")
        .defineInRange("unmarkedSyringeJitterFraction", 0.15, 0.0, 1.0, classOf[Double])
    )
    b.pop()
    s
  }
}
