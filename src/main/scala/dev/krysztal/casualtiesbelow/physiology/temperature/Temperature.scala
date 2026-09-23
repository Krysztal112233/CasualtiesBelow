package dev.krysztal.casualtiesbelow.physiology.temperature

import java.util.UUID

import scala.collection.mutable

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

import dev.krysztal.casualtiesbelow.api.event.BodyHeatContributionCallback
import dev.krysztal.casualtiesbelow.api.event.DryingBonusCallback
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataLookup
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataStore
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataStores
import dev.krysztal.casualtiesbelow.internal.extension.BiomeExtensions.*
import dev.krysztal.casualtiesbelow.physiology.dirtiness.Dirtiness

/** Advances body temperature and skin wetness once per player tick
  * ([[InjuryProgression.tickPlayer]]).
  *
  * Core temperature approaches an environment- and armor-adjusted equilibrium, with direct heat and
  * armor-scaled dissipation contributions. Wetness rises in water or rain and otherwise changes
  * through sweat and temperature- and air-dryness-driven drying. The fire-drying bonus affects
  * wetness only.
  *
  * The recurrence uses seconds; contribution and config rates use °C/min.
  */
object Temperature {

  /** Registers the built-in heat contributors. The progression itself is driven from
    * [[InjuryProgression.tickPlayer]], so no tick event is registered here.
    */
  def register(): Unit = {
    HeatContributions.register()
    HeatDamageContribution.register()
    DryingBonusCallback.EVENT.register(FireDryingBonus)
    ServerPlayConnectionEvents.DISCONNECT.register { (handler, _) =>
      ExertionTracker.discard(handler.player.getUUID);
      HeatDamageContribution.discard(handler.player.getUUID);
    }
  }

  /** Advances the player's body temperature and wetness by one tick. Returns whether the values
    * changed and this is a sync tick (continuously-changing values sync at 1 Hz, like Dirtiness).
    */
  private[casualtiesbelow] def tick(
      player: ServerPlayer,
      vitals: VitalsComponentImpl,
      syncTick: Boolean
  ): Boolean = {
    val environment = sampleEnvironment(player)
    val store = GameplayDataStores.server(player.level().getServer)
    val armor = armorThermalCoefficients(player, store, vitals.wetness)
    val frame = BodyHeatContributionCallback.Frame(
      armor.fireResistance,
      vitals.wetness,
      environment.airDryness
    )
    val heatContributions = collectHeatContributions(player, frame)

    val nextCore = calculateNextCoreTemperature(
      vitals.bodyTemperature,
      environment,
      armor,
      heatContributions
    )
    val coreChanged = VitalsMutations.setBodyTemperature(vitals, nextCore)
    val nextWetness = TemperatureCalc.nextWetness(
      vitals.wetness,
      wetnessDelta(player, environment, nextCore)
    )
    val wetnessChanged = VitalsMutations.setWetness(vitals, nextWetness)

    (coreChanged || wetnessChanged) && syncTick
  }

  private def sampleEnvironment(player: ServerPlayer): TemperatureEnvironment = {
    val level = player.level()
    val pos = player.blockPosition()
    val biome = level.getBiome(pos).value()
    val immersed = player.isInWater

    TemperatureEnvironment(
      apparentTemperature = TemperatureCalc.apparentTemperature(
        biome.mappedTemperature(pos, level.getSeaLevel),
        immersed
      ),
      airDryness = biome.airDryness,
      immersed = immersed,
      raining = !immersed && level.isRainingAt(pos)
    )
  }

  private def collectHeatContributions(
      player: ServerPlayer,
      frame: BodyHeatContributionCallback.Frame
  ): BodyHeatContributionCallback.Accumulator = {
    val context = new BodyHeatContributionCallback.Accumulator
    BodyHeatContributionCallback.EVENT
      .invoker()
      .contribute(player, frame, context)
    context
  }

  private def calculateNextCoreTemperature(
      coreTemperature: Double,
      environment: TemperatureEnvironment,
      armor: ArmorThermal,
      heatContributions: BodyHeatContributionCallback.Accumulator
  ): Double = {
    val equilibrium = CasualtiesBelowConfig.temperature.comfortBandFormula.evaluate(
      environment.apparentTemperature,
      CasualtiesBelowConfig.temperature.comfortLowCelsius.get(),
      CasualtiesBelowConfig.temperature.comfortHighCelsius.get(),
      CasualtiesBelowConfig.temperature.comfortSlope.get()
    )
    val effectiveEquilibrium =
      CasualtiesBelowConfig.temperature.effectiveTemperatureFormula.evaluate(
        equilibrium,
        armor.effectiveInsulation
      )

    TemperatureCalc.nextCoreTemperature(
      coreTemperature,
      effectiveEquilibrium,
      TemperatureCalc.approachRatePerSecond(
        CasualtiesBelowConfig.temperature.tauAirMinutes.get(),
        environment.immersed,
        CasualtiesBelowConfig.temperature.immersionRateMultiplier.get()
      ),
      TemperatureCalc.productionPerSecond(
        heatContributions.directTotal,
        heatContributions.dissipativeTotal,
        armor.effectiveDissipationBlock
      ),
      Consts.SecondsPerTick
    )
  }

  /** Coverage-weighted material coefficients over the four armor slots, with the wetness collapse
    * applied to insulation and dissipation block.
    */
  private def armorThermalCoefficients(
      player: ServerPlayer,
      store: GameplayDataStore,
      wetness: Double
  ): ArmorThermal = {
    var insulation = 0.0
    var dissipationBlock = 0.0
    var fireResistance = 0.0
    ArmorSlotWeights.foreach { (slot, weight) =>
      val stack = player.getItemBySlot(slot)
      if (!stack.isEmpty) {
        val thermal = GameplayDataLookup.materialThermal(stack, store)
        insulation += weight * thermal.insulation
        dissipationBlock += weight * thermal.dissipationBlock
        fireResistance += weight * thermal.fireResistance
      }
    }
    val collapse = CasualtiesBelowConfig.temperature.wetnessCollapseFormula
      .evaluate(wetness)
      .max(0.0)
      .min(1.0)
    ArmorThermal(
      insulation * collapse,
      dissipationBlock * collapse,
      fireResistance
    )
  }

  /** Wetness change from immersion, rain, sweat, and environmental drying. */
  private def wetnessDelta(
      player: ServerPlayer,
      environment: TemperatureEnvironment,
      nextCore: Double
  ): Double = {
    val exertionPerSecond =
      if (!environment.immersed) ExertionTracker.exhaustionPerSecond(player.getUUID) else 0.0
    val sweatPerSecond =
      if (
        exertionPerSecond > 0.0 &&
        nextCore > CasualtiesBelowConfig.temperature.sweatCoreTempThreshold.get()
      ) {
        Dirtiness.markSweating(player.getUUID)
        CasualtiesBelowConfig.temperature.sweatWetnessPerSecond
          .get() * TemperatureCalc.sweatRateFraction(
          exertionPerSecond,
          SprintExhaustionPerSecond
        )
      } else {
        0.0
      }
    if (environment.immersed) {
      CasualtiesBelowConfig.temperature.immersionWetnessPerSecond.get() * Consts.SecondsPerTick
    } else if (environment.raining) {
      CasualtiesBelowConfig.temperature.rainWetnessPerSecond
        .get() * Consts.SecondsPerTick + sweatPerSecond * Consts.SecondsPerTick
    } else {
      val dryingBonus = DryingBonusCallback.EVENT.invoker().dryingBonus(player)
      -CasualtiesBelowConfig.temperature.dryingCurveFormula.evaluate(
        environment.apparentTemperature + dryingBonus
      ) * environment.airDryness * Consts.SecondsPerTick + sweatPerSecond * Consts.SecondsPerTick
    }
  }

  private final case class TemperatureEnvironment(
      apparentTemperature: Double,
      airDryness: Double,
      immersed: Boolean,
      raining: Boolean
  )

  /** The three armor coefficients one tick pass consumes; insulation and dissipation block are
    * post-collapse (effective), fire resistance never collapses.
    */
  private final case class ArmorThermal(
      effectiveInsulation: Double,
      effectiveDissipationBlock: Double,
      fireResistance: Double
  )

  /** Drops this player's exertion tracking state when progression is skipped (death,
    * creative/spectator).
    */
  private[casualtiesbelow] def discard(player: ServerPlayer): Unit = {
    ExertionTracker.discard(player.getUUID)
    HeatDamageContribution.discard(player.getUUID)
  }

  /** Vanilla sprinting accrues exhaustion at ~0.56/s; it anchors the exertion fraction for both
    * exercise heat and sweating.
    */
  private val SprintExhaustionPerSecond = 0.56

  /** Being on fire flash-dries: a large apparent-temperature bonus for the drying curve only. */
  private object FireDryingBonus extends DryingBonusCallback {
    override def dryingBonus(player: ServerPlayer): Double =
      if (player.isOnFire) {
        CasualtiesBelowConfig.temperature.fireDryingBonusDegrees.get()
      } else {
        0.0
      }
  }

  /** Armor coverage weights by body surface: chest > legs > head ≈ feet. */
  private val ArmorSlotWeights: List[(EquipmentSlot, Double)] = List(
    (EquipmentSlot.CHEST, 0.35),
    (EquipmentSlot.LEGS, 0.3),
    (EquipmentSlot.HEAD, 0.2),
    (EquipmentSlot.FEET, 0.15)
  )
}
