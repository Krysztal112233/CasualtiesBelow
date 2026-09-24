package dev.krysztal.casualtiesbelow.config

import java.lang.Boolean
import java.lang.Double
import java.lang.Integer

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.config.sections.*
import dev.krysztal.casualtiesbelow.physiology.discomfort.DiscomfortDistribution as Distribution
import dev.krysztal.casualtiesbelow.physiology.pain.TotalPainStrategy
import dev.krysztal.casualtiesbelow.physiology.temperature.TemperatureCalc

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

/** Common (server-authoritative) configuration, backed by Forge Config API Port. Values are written
  * to `config/casualtiesbelow-common.toml` and can be edited in-game via the ModMenu integration
  * provided by Forge Config API Port.
  */
object CasualtiesBelowConfig {

  private val CurrentPhysiologyBalanceVersion = 1
  private val Builder = new ModConfigSpec.Builder()

  private val PhysiologyBalanceVersion: ConfigValue[Integer] = Builder
    .comment(
      "Internal migration marker for physiology balance defaults. Do not edit manually."
    )
    .defineInRange(
      "physiologyBalanceVersion",
      0,
      0,
      CurrentPhysiologyBalanceVersion
    )

  // Section definitions, in TOML order. The sequence here is what fixes the layout of
  // casualtiesbelow-common.toml; do not reorder casually.
  val vitals = VitalsValues.define(Builder)
  val hazards = HazardsValues.define(Builder)
  val bleeding = BleedingValues.define(Builder)
  val infection = InfectionValues.define(Builder)
  val sepsis = SepsisValues.define(Builder)
  val armor = ArmorValues.define(Builder)
  val discomfort = DiscomfortValues.define(Builder)
  val immune = ImmuneValues.define(Builder)
  val dirtiness = DirtinessValues.define(Builder)
  val regeneration = RegenerationValues.define(Builder)
  val movement = MovementValues.define(Builder)
  val pain = PainValues.define(Builder)
  val adrenaline = AdrenalineValues.define(Builder)
  val opioid = OpioidValues.define(Builder)
  val injection = InjectionValues.define(Builder)
  val fall = FallValues.define(Builder)
  val temperature = TemperatureValues.define(Builder)
  val progression = ProgressionValues.define(Builder)
  val visuals = VisualsValues.define(Builder)

  private val Spec = Builder.build()

  /** Immune health at which infection spread and immune fight exactly cancel out for a single
    * infection: `max × spread / (spread + fight)`. Below it infections spread, above it they
    * recede. With several infected limbs the fight capacity is split, so the effective break-even
    * rises with the infection count.
    */
  def immuneBreakEven: Double = {
    val spread = infection.infectionSpreadPerTick.get()
    val fight = infection.infectionFightPerTick.get()
    if (spread + fight <= 0.0) {
      0.0
    } else {
      vitals.maxImmuneHealth.get() * spread / (spread + fight)
    }
  }

  /** The effective blood volume cap: sepsis compresses it linearly, down to zero at full sepsis
    * (which is fatal). Blood over the cap is lost — recovering requires eating well (see
    * [[vitals.fedBloodRegenPerTick]]).
    */
  def effectiveMaxBloodVolume(sepsis: Double): Double = {
    vitals.maxBloodVolume
      .get() * (1.0 - (sepsis / CasualtiesBelowConfig.sepsis.maxSepsis.get()).min(1.0))
  }

  /** Cross-field consciousness thresholds used by server progression and synchronized displays. */
  private[casualtiesbelow] def effectiveConsciousnessFloor: Double = {
    finiteThreshold(vitals.consciousnessFloor.get(), 0.0)
  }

  private[casualtiesbelow] def effectiveConsciousnessKnockoutThreshold: Double = {
    finiteThreshold(vitals.consciousnessKnockoutThreshold.get(), effectiveConsciousnessFloor)
  }

  private[casualtiesbelow] def effectiveConsciousnessWakeThreshold: Double = {
    val knockout = effectiveConsciousnessKnockoutThreshold
    val minimumWake =
      (knockout + VitalsComponent.MinimumWakeThreshold).min(VitalsComponent.MaxValue)
    finiteThreshold(vitals.consciousnessWakeThreshold.get(), minimumWake)
  }

  private def finiteThreshold(
      value: scala.Double,
      minimum: scala.Double
  ): scala.Double = {
    if (value == scala.Double.PositiveInfinity) VitalsComponent.MaxValue
    else if (value.isFinite) value.max(minimum).min(VitalsComponent.MaxValue)
    else minimum
  }

  def register(): Unit = {
    ConfigRegistry.INSTANCE.register(CasualtiesBelow.ModId, ModConfig.Type.COMMON, Spec)
    migratePhysiologyBalanceDefaults()
  }

  /** FCAP preserves every existing valid value when only a spec default changes. Migrate values
    * that still equal the previous release defaults, while retaining genuinely customized values.
    * The marker starts at zero so both a pre-marker file and a fresh file take this idempotent
    * pass; fresh files already contain the new values and therefore only advance the marker.
    */
  private def migratePhysiologyBalanceDefaults(): Unit = {
    if (PhysiologyBalanceVersion.get().intValue >= CurrentPhysiologyBalanceVersion) return

    migratePreviousDefault(vitals.consciousnessIncapacitationStartThreshold, 30.0, 50.0)
    migratePreviousDefault(vitals.bloodOxygenDepletionPerTick, 0.3, 0.4)
    migratePreviousDefault(vitals.bloodOxygenRecoveryPerTick, 0.5, 0.8)
    migratePreviousDefault(vitals.consciousnessRecoveryPerTick, 0.08, 0.2)
    migratePreviousDefault(vitals.consciousnessWakeThreshold, 20.0, 40.0)
    migratePreviousDefault(hazards.inWallBloodOxygenDepletionPerTick, 0.5, 0.6)
    if (hazards.terminalHypoxiaDurationTicks.get().intValue == 200) {
      hazards.terminalHypoxiaDurationTicks.set(160)
    }

    PhysiologyBalanceVersion.set(CurrentPhysiologyBalanceVersion)
    Spec.save()
    CasualtiesBelow.Logger.info(
      "Migrated physiology balance defaults to version {}",
      CurrentPhysiologyBalanceVersion
    )
  }

  private def migratePreviousDefault(
      value: ConfigValue[Double],
      previousDefault: Double,
      currentDefault: Double
  ): Unit = {
    if (
      java.lang.Double.doubleToLongBits(value.get().doubleValue) ==
        java.lang.Double.doubleToLongBits(previousDefault)
    ) {
      value.set(currentDefault)
    }
  }
}
