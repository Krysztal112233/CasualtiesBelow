package dev.krysztal.casualtiesbelow.internal

import net.minecraft.world.entity.EquipmentSlot

import dev.krysztal.casualtiesbelow.config.FormulaConfigValue
import dev.krysztal.casualtiesbelow.physiology.discomfort.DiscomfortDistribution

/** Fixed balance defaults and vanilla invariants. Player-tunable values remain in
  * [[dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig]]; the former low-level config
  * settings live here so gameplay, synchronization and displays share one source of truth.
  */
private[casualtiesbelow] object Consts {

  /** Vanilla runs twenty ticks per second. */
  val TicksPerSecond: Int = 20

  /** One tick in seconds. */
  val SecondsPerTick: Double = 1.0 / TicksPerSecond

  /** The four equipment slots making up a full armor set, in display order (head first). */
  val ArmorSlots: List[EquipmentSlot] =
    List(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)

  object Vitals {
    val StartingHealth = 100.0
    val StartingConsciousness = 100.0
    val ConsciousnessImpairmentStartThreshold = 50.0
    val ConsciousnessFloor = 10.0
    val ConsciousnessKnockoutThreshold = 30.0
    val BloodOxygenDepletionPerTick = 0.4
    val BloodOxygenRecoveryPerTick = 0.8
    val BloodOxygenHypoxiaThreshold = 50.0
    val ConsciousnessOxygenCapMultiplier = 1.2
    val ConsciousnessRecoveryOxygenThreshold = 75.0
    val ConsciousnessRecoveryPerTick = 0.2
    val ConsciousnessWakeThreshold = 40.0
    val MaxBloodVolume = 5000.0
    val FullOxygenBloodFraction = 0.6
    val FedBloodRegenPerTick = 0.05
    val MaxImmuneHealth = 200.0

    /** Effective blood capacity after sepsis: linearly scaled down from [[MaxBloodVolume]], to zero
      * at full sepsis.
      */
    def effectiveMaxBloodVolume(sepsis: Double): Double = {
      MaxBloodVolume * (1.0 - (sepsis / Sepsis.MaxSepsis).min(1.0))
    }
  }

  object Hazards {
    val TerminalHypoxiaDurationTicks = 160
    val InWallBloodOxygenDepletionPerTick = 0.6
    val StarvationBloodLossFractionPerDamage = 0.05
    val EasyStarvationBloodFloorFraction = 0.5
    val NormalStarvationBloodFloorFraction = 0.05
  }

  object Bleeding {
    val ClottingRatePerTick = 0.00004
    val TotemBloodRestoreFraction = 0.2
    val TotemHemostasisInitialReduction = 0.8
    val TotemHemostasisDurationTicks = 600
    val NotTodayNearMaxBleedingFraction = 0.75
  }

  object Infection {
    val InfectionChancePerTick = 0.0005
    val InfectionSpreadPerTick = 0.03
    val InfectionFightPerTick = 0.02
    val InfectionEffectStartProgress = 20.0
    val InfectionEffectFullProgress = 40.0
    val InfectionPainPerTick = 0.05
    val InfectionMuscleDecayPerTick = 0.005
    val InfectionContagionStartProgress = 60.0
    val InfectionContagionFullProgress = 80.0
    val InfectionContagionMaxChancePerTick = 0.05

    /** Immune level at which infection spread and immune fight exactly balance out. */
    def immuneBreakEven: Double = {
      val spread = InfectionSpreadPerTick
      val fight = InfectionFightPerTick
      Vitals.MaxImmuneHealth * spread / (spread + fight)
    }
  }

  object Immune {

    /** Fresh-player and respawn immune health: deliberately exactly the single-limb infection
      * break-even ([[Infection.immuneBreakEven]]), so infections demand active management from the
      * start instead of passively clearing. The ceiling stays [[Vitals.MaxImmuneHealth]].
      */
    val DefaultImmuneHealth = 120.0

    val FedImmuneRegenPerTick = 0.005
    val HungryImmuneDrainPerTick = 0.01
    val FedFoodLevelThreshold = 18
    val HungryFoodLevelThreshold = 7
    val ZombieHitImmuneDrain = 5.0
    val PoisonImmuneDrainPerTick = 0.05
  }

  object Sepsis {
    val MaxSepsis = 100.0
    val SepsisGainPerTick = 0.05
    val SepsisDecayPerTick = 0.03
  }

  object Regeneration {
    val SkinRestorePerTick = 0.001
    val SkinRegenMinImmuneMultiplier = 0.25
    val SkinRegenMinDirtinessMultiplier = 0.75

    /** Skin integrity restored per tick per amplifier level by the Skin Regeneration effect. */
    val SkinRegenerationEffectPerTick = 0.05

    /** Duration of the brewed Skin Regeneration potion, in ticks. */
    val SkinRegenerationPotionTicks = 1800

    /** Muscle health restored per tick per amplifier level by the Muscle Recovery effect. */
    val MuscleRecoveryEffectPerTick = 0.05

    /** Duration of the brewed Muscle Recovery potion, in ticks. */
    val MuscleRecoveryPotionTicks = 1800

    /** Fracture recovery ticks advanced per tick per amplifier level by the Bone Healing effect. */
    val BoneHealingEffectPerTick = 2.0

    /** Duration of the brewed Bone Healing potion, in ticks. */
    val BoneHealingPotionTicks = 1800
  }

  object Armor {
    val ArmorSkinFactorFormula = FixedFormula(
      "max(0.05, min(1, 1 - armor * 0.1 - toughness * 0.02))",
      List("armor", "toughness")
    )
    val ArmorMuscleFactorFormula = FixedFormula(
      "1 - (1 - skinFactor) * 0.5",
      List("armor", "toughness", "skinFactor")
    )
  }

  object Fall {
    val FallDamageFormula = FixedFormula(
      "max(0, distance - safeDistance)^1.5 * 0.5 * modifier * multiplier",
      List("distance", "safeDistance", "modifier", "multiplier")
    )
    val BootsCushionFormula = FixedFormula(
      "min(0.5, armor * 0.07 + toughness * 0.04)",
      List("armor", "toughness")
    )
    val LeggingsConditionProtectionFormula = FixedFormula(
      "min(0.6, armor * 0.06 + toughness * 0.05)",
      List("armor", "toughness")
    )
  }

  object Movement {
    val DislocationSpeedReduction = 0.3
    val DislocationJumpReduction = 0.2
    val MuscleSpeedReduction = 0.75
    val MuscleJumpReduction = 0.6
  }

  object Pain {
    val TotalPainStrategy = dev.krysztal.casualtiesbelow.physiology.pain.TotalPainStrategy.Geometric
    val TotalPainDecay = 0.3
    val TotalPainFilterThreshold = 0.0
    val PainDecayPerTick = 0.025
    val ShockAccumulationStartPain = 70.0
    val ShockMaximumRatePain = 80.0
    val ShockMaximumGainPerTick = 0.2
    val ShockRecoveryPerTick = 0.1
    val ShockCollapseThreshold = 90.0
    val ShockWakeLoadCap = 0.0
    val FracturedWalkingPainPerTick = 0.5
    val DislocatedWalkingPainPerTick = 0.3
  }

  object Adrenaline {
    val MaxValue = 100.0
    val DecayPerTick = 0.1
    val CombatGraceTicks = 100
    val ShockProtectionPerPoint = 1.0
    val PainReductionPerPoint = 0.005
    val MaxPainReductionFraction = 0.5
  }

  object Opioid {
    val LevelDecayPerTick = 0.0083
    val DependenceExposurePerLevelPerTick = 0.0000125
    val DependenceDecayPerTick = 0.000125
    val OpioidPainDrainPerLevelPerTick = 0.0025
    val OpioidSedationCeilingFormula = FixedFormula(
      "100 - 0.5 * max(0, level - 110)",
      List("level")
    )
    val OpioidRespiratoryEfficiencyFormula = FixedFormula(
      "1 - 0.9 * max(0, level - (120 + min(20, dependence * 0.2))) / 80",
      List("level", "dependence")
    )
    val RespiratoryFailureEfficiencyThreshold = 0.3
    val RespiratoryFailureOxygenDrainPerTick = 0.3
    val WithdrawalDependenceThreshold = 20.0
    val WithdrawalLevelPerDependence = 0.6
    val WithdrawalPainMultiplier = 1.25
    val WithdrawalDiscomfortPerTick = 0.0025
    val WithdrawalDiscomfortTarget = 35.0
    val CrudeSyringeDoseSigma = 13.0
    val UnmarkedSyringeJitterFraction = 0.15
  }

  object Injection {
    val MaxSpeedFractionPerSecond = 0.5
    val FullDoseSideEffectDiscomfort = 10.0
    val FullDoseSideEffectPain = 10.0
    val FullSpeedPressDepthPixels = 60
    val BatchIntervalMilliseconds = 200
  }

  object Temperature {
    val ImmersionRateMultiplier = 2.0
    val ComfortLowCelsius = 10.0
    val ComfortHighCelsius = 28.0
    val ComfortSlope = 0.3
    val PenaltyBandLowCelsius = 35.0
    val PenaltyBandHighCelsius = 39.5
    val ColdConsciousnessSlopePerDegree = 5.0
    val HotConsciousnessSlopePerDegree = 5.0
    val ColdImmuneDrainPerDegreePerMinute = 1.0
    val HotImmuneDrainPerDegreePerMinute = 0.5
    val SweatCoreTempThreshold = 37.0
    val SweatWetnessPerSecond = 0.15
    val SweatDirtinessMultiplier = 1.5
    val EvaporationCoolingPerMinute = 0.3
    val ExerciseHeatPerExhaustionPerSecond = 0.9
    val HeatStrongPerMinute = 14.0
    val HeatExtremePerMinute = 32.0
    val HeatNormalPerMinute = 8.0
    val FireDryingBonusDegrees = 60.0
    val ImmersionWetnessPerSecond = 0.5
    val RainWetnessPerSecond = 0.02
    val BiomeMappingFormula = FixedFormula("(t - 0.15) * 40 / 1.85", List("t"))
    val ComfortBandFormula = FixedFormula(
      "if(t < low, 37 + (t - low) * slope, if(t > high, 37 + (t - high) * slope, 37))",
      List("t", "low", "high", "slope")
    )
    val EffectiveTemperatureFormula = FixedFormula(
      "37 + (t - 37) * (1 - i * if(t > 37, 0, 1))",
      List("t", "i")
    )
    val TemperatureConsciousnessCeilingFormula = FixedFormula(
      "100 - coldDev * coldSlope - hotDev * hotSlope",
      List("coldDev", "hotDev", "coldSlope", "hotSlope")
    )
    val DryingCurveFormula = FixedFormula("0.0014 * 2.718281828459045^(0.06 * t)", List("t"))
    val WetnessCollapseFormula = FixedFormula("1 - 0.85 * wetness", List("wetness"))

    val BlockTemperatureRadius = 4
    val BlockHeatContribute1PerMinute = 4.0
    val BlockHeatContribute2PerMinute = 4.0
    val BlockHeatContribute3PerMinute = 4.0
    val BlockColdContribute1PerMinute = 4.0
    val BlockColdContribute2PerMinute = 4.0
    val BlockColdContribute3PerMinute = 4.0
  }

  object Dirtiness {
    val MaxValue = 100.0
    val AccrualPerSecond = 0.03
    val SprintMultiplier = 2.5
    val ArmoredMultiplier = 1.3
    val NetherMultiplier = 1.5
    val WashRainPerSecond = 0.6
    val CauldronPointsPerLevel = 34.0
    val DirtyWaterWashMultiplier = 0.5
    val CombatPulseDirt = 3.0
    val DiggingPulseDirt = 0.02
    val InteractionPulseDirt = 0.5
    val FoodDirtFraction = 0.1
    val InfectionChanceMultiplierAtMax = 2.0
    val InjectionSeedAtMax = 12.0
    val FoodDiscomfortMultiplierAtMax = 0.5
    val ImmuneDrainStartDirtiness = 50.0
    val ImmuneDrainMaxPerTick = 0.01
  }

  object Discomfort {
    val MaxValue = 100.0
    val Distribution = DiscomfortDistribution.Gaussian
    val Level1Mean = 7.0
    val Level2Mean = 15.0
    val Level3Mean = 30.0
    val NauseaThreshold = 30.0
    val DecayRateLowPerSecond = 0.5
    val DecayRateHighPerSecond = 0.2
    val AlreadyNauseousMultiplier = 1.25
    val OvereatingMultiplier = 1.25
    val PoorConditionMultiplier = 1.5
    val PoorConditionConsciousnessThreshold = 50.0
    val VomitChanceThreshold = 30.0
    val VomitMinChancePerTick = 0.0005
    val VomitMaxChancePerTick = 0.00256
    val VomitRelief = 30.0
    val VomitHungerPenalty = 6
    val VomitSaturationPenalty = 8.0
  }

  object Randomness {
    val WorldPulseJitter = 0.3
    val DoseSpreadFraction = 0.2
  }

  object Visuals {
    val ConsciousnessMaxDimOpacity = 0.55
    val BloodDesaturationStartFraction = 0.9
    val BloodFullDesaturationFraction = 0.3
    val NauseaVignetteMaxOpacity = 0.35
    val DirtinessBandGrimy = 30.0
    val DirtinessBandFilthy = 60.0
    val DirtinessBandSqualid = 85.0
    val GrimeVignetteMaxOpacity = 0.55
    val ShockVisualStartLoad = 0.0
    val ShockVisualPulseStrength = 0.2
    val ShockVisualNoiseStrength = 0.25
    val FrostOverlayStartCelsius = 35.0
    val FrostOverlayFullSpanCelsius = 6.0
    val FrostOverlayMaxStrength = 0.85
    val HeatOverlayStartCelsius = 39.5
    val HeatOverlayFullSpanCelsius = 2.5
    val HeatOverlayMaxStrength = 0.85
  }

  /** Compiled fixed EvalEx curve. Like the former configurable evaluator, expressions are reused
    * with mutable variable bindings and must be evaluated on the owning game thread.
    */
  final class FixedFormula(val source: String, variables: List[String]) {
    private val expression = FormulaConfigValue.compile(source, variables)

    def evaluate(values: Double*): Double = {
      require(values.length == variables.length)
      variables.lazyZip(values).foreach { (name, value) =>
        expression.`with`(name, value)
      }
      expression.evaluate().getNumberValue.doubleValue()
    }
  }

  object FixedFormula {
    def apply(source: String, variables: List[String]): FixedFormula =
      new FixedFormula(source, variables)
  }
}
