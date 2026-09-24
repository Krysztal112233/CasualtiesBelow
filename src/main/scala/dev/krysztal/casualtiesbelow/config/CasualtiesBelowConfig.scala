package dev.krysztal.casualtiesbelow.config

import java.lang.Boolean
import java.lang.Double

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.internal.Consts

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

/** A small set of server-authoritative gameplay choices and presentation controls, written to
  * `config/casualtiesbelow-common.toml`. Technical balance defaults live in [[Consts]] rather than
  * appearing in the in-game editor. Previous config keys are intentionally reset on upgrade.
  */
object CasualtiesBelowConfig {

  private val Builder = new ModConfigSpec.Builder()

  private def group[A](name: String)(values: => A): A = {
    Builder.push(name)
    val result = values
    Builder.pop()
    result
  }

  final case class InjurySurvival(
      maxExternalBleedingRate: ConfigValue[Double],
      adrenalineEnabled: ConfigValue[Boolean],
      fallDamageMultiplier: ConfigValue[Double],
      clottingSpeedMultiplier: ConfigValue[Double],
      naturalHealingMultiplier: ConfigValue[Double],
      legMovementPenaltyMultiplier: ConfigValue[Double]
  )

  final case class DiseaseHygiene(
      infectionEnabled: ConfigValue[Boolean],
      washWaterPerSecond: ConfigValue[Double],
      woundInfectionRiskMultiplier: ConfigValue[Double],
      dirtAccumulationMultiplier: ConfigValue[Double]
  )

  final case class Environment(
      tauAirMinutes: ConfigValue[Double],
      comfortLowCelsius: ConfigValue[Double],
      comfortHighCelsius: ConfigValue[Double]
  ) {
    def effectiveComfortBounds: (scala.Double, scala.Double) = {
      val low = comfortLowCelsius.get().doubleValue
      val high = comfortHighCelsius.get().doubleValue
      (low.min(high), low.max(high))
    }
  }

  final case class MedicineFood(
      refinedSyringeDose: ConfigValue[Double],
      crudeSyringeDoseMean: ConfigValue[Double],
      refusalThreshold: ConfigValue[Double],
      opioidPainReliefMultiplier: ConfigValue[Double]
  )

  final case class Visuals(
      temperatureOverlayEnabled: ConfigValue[Boolean],
      consciousnessMaxBlurStrength: ConfigValue[Double],
      shockVisualMaxStrength: ConfigValue[Double]
  )

  val injurySurvival: InjurySurvival = group("injurySurvival") {
    InjurySurvival(
      maxExternalBleedingRate = Builder
        .comment(
          "Maximum external bleeding from one severely wounded limb, in mL per tick.",
          "Lower this to make blood loss less dangerous; intact skin still cannot bleed."
        )
        .defineInRange("maxExternalBleedingRate", 1.0, 0.0, 100.0, classOf[Double]),
      adrenalineEnabled = Builder
        .comment(
          "Allow new adrenaline bursts from damage. Disabling this does not erase an existing",
          "adrenaline reserve; it can still decay normally."
        )
        .define("adrenalineEnabled", true),
      fallDamageMultiplier = Builder
        .comment(
          "Player fall damage relative to the standard custom fall curve (1 = normal).",
          "Set to zero to prevent player fall damage; mobs keep vanilla fall damage."
        )
        .defineInRange("fallDamageMultiplier", 1.0, 0.0, 3.0, classOf[Double]),
      clottingSpeedMultiplier = Builder
        .comment("How quickly an external wound stops bleeding (1 = normal, 0 = no clotting).")
        .defineInRange("clottingSpeedMultiplier", 1.0, 0.0, 5.0, classOf[Double]),
      naturalHealingMultiplier = Builder
        .comment(
          "Natural skin and muscle healing speed (1 = normal, 0 = no natural healing).",
          "Does not affect the vanilla Regeneration effect or wound clotting."
        )
        .defineInRange("naturalHealingMultiplier", 1.0, 0.0, 5.0, classOf[Double]),
      legMovementPenaltyMultiplier = Builder
        .comment(
          "How strongly injured legs slow movement and weaken jumps (1 = normal, 0 = none).",
          "Applies to fractures, dislocations and muscle damage; restart the game to change."
        )
        .gameRestart()
        .defineInRange("legMovementPenaltyMultiplier", 1.0, 0.0, 1.0, classOf[Double])
    )
  }

  val diseaseHygiene: DiseaseHygiene = group("diseaseHygiene") {
    DiseaseHygiene(
      infectionEnabled = Builder
        .comment(
          "Allow new infections from wounds or dirty needles, and spread to nearby limbs.",
          "Existing infections can still progress and cause sepsis until they recover."
        )
        .define("infectionEnabled", true),
      washWaterPerSecond = Builder
        .comment("Dirt washed off per second while immersed in clean water.")
        .defineInRange("washWaterPerSecond", 4.8, 0.0, 100.0, classOf[Double]),
      woundInfectionRiskMultiplier = Builder
        .comment(
          "Chance of a wound becoming infected (1 = normal, 0 = no wound-onset infections).",
          "Dirty needles and spread from an existing infection are controlled by infectionEnabled."
        )
        .defineInRange("woundInfectionRiskMultiplier", 1.0, 0.0, 5.0, classOf[Double]),
      dirtAccumulationMultiplier = Builder
        .comment(
          "Dirt gained from activity and surroundings (1 = normal, 0 = no new dirt).",
          "Does not change the rate at which water or rain washes dirt away."
        )
        .defineInRange("dirtAccumulationMultiplier", 1.0, 0.0, 5.0, classOf[Double])
    )
  }

  val environment: Environment = group("environment") {
    Environment(
      tauAirMinutes = Builder
        .comment(
          "Minutes for the body to adjust toward the surrounding temperature in still air:",
          "about 63% of the gap closes in this time. Lower values warm and cool faster."
        )
        .defineInRange("tauAirMinutes", 3.0, 0.1, 60.0, classOf[Double]),
      comfortLowCelsius = Builder
        .comment(
          "Coldest apparent air temperature (°C) that keeps body temperature comfortable.",
          "If this exceeds the upper bound, the two values are swapped."
        )
        .defineInRange(
          "comfortLowCelsius",
          Consts.Temperature.ComfortLowCelsius,
          -50.0,
          37.0,
          classOf[Double]
        ),
      comfortHighCelsius = Builder
        .comment(
          "Warmest apparent air temperature (°C) that keeps body temperature comfortable.",
          "If this falls below the lower bound, the two values are swapped."
        )
        .defineInRange(
          "comfortHighCelsius",
          Consts.Temperature.ComfortHighCelsius,
          -50.0,
          80.0,
          classOf[Double]
        )
    )
  }

  val medicineFood: MedicineFood = group("medicineFood") {
    MedicineFood(
      refinedSyringeDose = Builder
        .comment("Base opioid dose from one refined poppy ampoule.")
        .defineInRange("refinedSyringeDose", 50.0, 0.0, 200.0, classOf[Double]),
      crudeSyringeDoseMean = Builder
        .comment(
          "Average opioid dose drawn directly from crude poppy liquid; actual doses vary."
        )
        .defineInRange("crudeSyringeDoseMean", 40.0, 0.0, 200.0, classOf[Double]),
      refusalThreshold = Builder
        .comment("Discomfort at which the player refuses to start eating unpleasant food.")
        .defineInRange("refusalThreshold", 60.0, 0.0, 10000.0, classOf[Double]),
      opioidPainReliefMultiplier = Builder
        .comment(
          "Pain relief from opioid exposure (1 = normal, 0 = no opioid pain relief).",
          "Sedation, breathing risks and dependence are not affected."
        )
        .defineInRange("opioidPainReliefMultiplier", 1.0, 0.0, 5.0, classOf[Double])
    )
  }

  val visuals: Visuals = group("visuals") {
    Visuals(
      temperatureOverlayEnabled = Builder
        .comment("Show frost and heat effects on the screen when body temperature is unsafe.")
        .define("temperatureOverlayEnabled", true),
      consciousnessMaxBlurStrength = Builder
        .comment(
          "Maximum blur and double-vision when consciousness is low (0-1).",
          "Set to zero to remove blur; unconscious blackout still applies."
        )
        .defineInRange("consciousnessMaxBlurStrength", 0.99, 0.0, 1.0, classOf[Double]),
      shockVisualMaxStrength = Builder
        .comment(
          "Maximum strength of the peripheral pain-shock warning (0-1).",
          "Set to zero to hide the warning without changing gameplay."
        )
        .defineInRange("shockVisualMaxStrength", 1.0, 0.0, 1.0, classOf[Double])
    )
  }

  private val Spec = Builder.build()

  def register(): Unit =
    ConfigRegistry.INSTANCE.register(CasualtiesBelow.ModId, ModConfig.Type.COMMON, Spec)
}
