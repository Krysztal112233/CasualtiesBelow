package dev.krysztal.casualtiesbelow.config.sections

import dev.krysztal.casualtiesbelow.config.FormulaConfigValue

import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

private[config] final case class ArmorValues(
    armorSkinFactorFormula: FormulaConfigValue,
    armorMuscleFactorFormula: FormulaConfigValue
)

private[config] object ArmorValues {

  def define(b: ModConfigSpec.Builder): ArmorValues = {
    b.push("armor")
    val s = ArmorValues(
      armorSkinFactorFormula = new FormulaConfigValue(
        b,
        "skinFactorFormula",
        "max(0.05, min(1, 1 - armor * 0.1 - toughness * 0.02))",
        List("armor", "toughness"),
        comment = Seq(
          "Skin damage multiplier from the piece covering the struck body part, compiled with",
          "EvalEx (https://github.com/ezylang/EvalEx). Available variables: armor (armor points),",
          "toughness (armor toughness points). Skin and bleeding coefficients of the wound profile",
          "are multiplied by this factor. Invalid formulas are rejected and corrected to the default.",
          "Hot-reloaded on file change."
        )
      ),
      armorMuscleFactorFormula = new FormulaConfigValue(
        b,
        "muscleFactorFormula",
        "1 - (1 - skinFactor) * 0.5",
        List("armor", "toughness", "skinFactor"),
        comment =
          Seq(
            "Muscle damage multiplier from the covering armor piece: the blunt impact that still",
            "transmits through the armor. Available variables: armor, toughness, skinFactor (the",
            "evaluated skinFactorFormula result). Muscle and pain coefficients of the wound profile",
            "are multiplied by this factor."
          )
      )
    )
    b.pop()
    s
  }
}
