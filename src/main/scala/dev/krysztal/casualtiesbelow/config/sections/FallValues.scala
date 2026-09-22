package dev.krysztal.casualtiesbelow.config.sections

import java.lang.Boolean

import dev.krysztal.casualtiesbelow.config.FormulaConfigValue

import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

private[config] final case class FallValues(
    fallDamageFormula: FormulaConfigValue,
    affectAllLivingEntities: ConfigValue[Boolean],
    bootsCushionFormula: FormulaConfigValue,
    leggingsConditionProtectionFormula: FormulaConfigValue
)

private[config] object FallValues {

  def define(b: ModConfigSpec.Builder): FallValues = {
    b.push("fall")
    val s = FallValues(
      fallDamageFormula = new FormulaConfigValue(
        b,
        "damageFormula",
        "max(0, distance - safeDistance)^1.5 * 0.5 * modifier * multiplier",
        List("distance", "safeDistance", "modifier", "multiplier"),
        comment = Seq(
          "Fall damage formula, compiled with EvalEx (https://github.com/ezylang/EvalEx).",
          "Available variables: distance (fall distance), safeDistance (safe fall distance attribute),",
          "modifier (vanilla damage modifier), multiplier (fall damage multiplier attribute).",
          "Invalid formulas are rejected and corrected to the default. Hot-reloaded on file change."
        )
      ),
      affectAllLivingEntities = b
        .comment(
          "Whether the custom fall damage formula applies to all living entities, not just players."
        )
        .gameRestart()
        .define("affectAllLivingEntities", false),
      bootsCushionFormula = new FormulaConfigValue(
        b,
        "bootsCushionFormula",
        "min(0.5, armor * 0.07 + toughness * 0.04)",
        List("armor", "toughness"),
        comment = Seq(
          "Fraction of the fall impact on the legs absorbed by worn boots (0 = none), compiled with",
          "EvalEx. Available variables: armor, toughness (the boots' attribute values in the feet slot).",
          "Boots with neither attribute cushion nothing. Invalid formulas are rejected and corrected",
          "to the default. Hot-reloaded on file change."
        )
      ),
      leggingsConditionProtectionFormula = new FormulaConfigValue(
        b,
        "leggingsConditionProtectionFormula",
        "min(0.6, armor * 0.06 + toughness * 0.05)",
        List("armor", "toughness"),
        comment = Seq(
          "Fraction of the fall impact ignored when rolling the dislocation/fracture thresholds,",
          "from worn leggings, compiled with EvalEx. Available variables: armor, toughness (the",
          "leggings' attribute values in the legs slot). Invalid formulas are rejected and corrected",
          "to the default. Hot-reloaded on file change."
        )
      )
    )
    b.pop()
    s
  }
}
