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

/** Body temperature progression: the environment pulls the core temperature towards an equilibrium
  * while internal heat production acts on it directly.
  *
  * Every tick, per player (driven from [[InjuryProgression.tickPlayer]]):
  *
  *   - the vanilla height-adjusted biome temperature maps to an apparent temperature (°C); while
  *     immersed, the water takes over with a 0°C floor for liquid water
  *   - the comfort band maps the apparent temperature to an equilibrium core temperature: inside
  *     the band the equilibrium is normal body temperature (no drift), outside it deviates by the
  *     configured slope
  *   - armor weakens the equilibrium's pull: each worn piece contributes its material's insulation
  *     and dissipation-block coefficients (data-driven, keyed by equipment asset id) weighted by
  *     body coverage; wetness collapses both coefficients together, so soaked armor neither keeps
  *     you warm nor keeps you stifled
  *   - heat contributions arrive through [[BodyHeatContributionCallback]]: `addDirect` bypasses
  *     armor (exercise heat, fire/lava contact heat), `addDissipative` is scaled by the armor's
  *     surviving dissipation block (evaporative cooling)
  *   - the core temperature approaches the armor-weakened equilibrium exponentially with the
  *     configured time constant (faster while immersed), plus the production terms
  *
  * The wetness axis (0..1) accrues while immersed or in rain and dries along a temperature-driven
  * curve scaled by air dryness (`1 - downfall`); [[DryingBonusCallback]] feeds extra drying
  * temperature (being on fire) into that curve only — it never enters the core-temperature formula.
  *
  * Units: the recurrence runs in seconds (Δt = 1/20 s, rates per second); contribution events and
  * their config values speak °C per minute and are divided by 60 at the point of application.
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
    val level = player.level()
    val pos = player.blockPosition()
    val biome = level.getBiome(pos).value()
    val immersed = player.isInWater

    // (a) Apparent temperature: biome-mapped; while immersed the water takes over and liquid
    // water never goes below freezing. Air dryness (1 - downfall) drives evaporation later.
    val apparent = TemperatureCalc.apparentTemperature(
      biome.mappedTemperature(pos, level.getSeaLevel),
      immersed
    )
    val airDryness = biome.airDryness

    // (c)(d) Armor scan with wetness collapse folded in: soaked armor neither insulates nor
    // stifles. Fire resistance is the fire layer's own coefficient and does not collapse.
    val armor =
      armorThermalCoefficients(player, GameplayDataStores.server(level.getServer), vitals.wetness)

    // (e) Heat contributions. The frame carries this tick's armor/wetness context to the
    // listeners; dispatch is synchronous on the server thread. A fresh accumulator per dispatch:
    // partial sums die with the object if a listener throws, no reset to remember.
    val frame =
      BodyHeatContributionCallback.Frame(armor.fireResistance, vitals.wetness, airDryness)
    val context = new BodyHeatContributionCallback.Accumulator
    BodyHeatContributionCallback.EVENT
      .invoker()
      .contribute(player, frame, context)

    // (b)(f) Equilibrium from the comfort band, then the armor-weakened exponential approach
    // plus the production terms.
    val (coreChanged, nextCore) = {
      val equilibrium = CasualtiesBelowConfig.temperature.comfortBandFormula.evaluate(
        apparent,
        CasualtiesBelowConfig.temperature.comfortLowCelsius.get(),
        CasualtiesBelowConfig.temperature.comfortHighCelsius.get(),
        CasualtiesBelowConfig.temperature.comfortSlope.get()
      )
      val effectiveEquilibrium =
        CasualtiesBelowConfig.temperature.effectiveTemperatureFormula.evaluate(
          equilibrium,
          armor.effectiveInsulation
        )
      val nextCore = TemperatureCalc.nextCoreTemperature(
        vitals.bodyTemperature,
        effectiveEquilibrium,
        TemperatureCalc.approachRatePerSecond(
          CasualtiesBelowConfig.temperature.tauAirMinutes.get(),
          immersed,
          CasualtiesBelowConfig.temperature.immersionRateMultiplier.get()
        ),
        TemperatureCalc.productionPerSecond(
          context.directTotal,
          context.dissipativeTotal,
          armor.effectiveDissipationBlock
        ),
        Consts.SecondsPerTick
      )

      (VitalsMutations.setBodyTemperature(vitals, nextCore), nextCore)
    }

    // (g) Wetness axis, then store both results.
    val wetnessChanged =
      VitalsMutations.setWetness(
        vitals,
        TemperatureCalc.nextWetness(
          vitals.wetness,
          wetnessDelta(player, apparent, immersed, airDryness, nextCore)
        )
      )

    // Throttle like Dirtiness: the approach never exactly converges, so core is always dirty;
    // contribute to the sync decision only on sync ticks (the state itself updates every tick).
    (coreChanged || wetnessChanged) && syncTick
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

  /** Per-tick wetness change: fast accrual while immersed, slow in rain, otherwise drying — with
    * sweat added on top of the non-immersion branches while the new core temperature runs hot.
    */
  private def wetnessDelta(
      player: ServerPlayer,
      apparent: Double,
      immersed: Boolean,
      airDryness: Double,
      nextCore: Double
  ): Double = {
    val level = player.level()
    val pos = player.blockPosition()
    val exertionPerSecond =
      if (!immersed) ExertionTracker.exhaustionPerSecond(player.getUUID) else 0.0
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
    if (immersed) {
      CasualtiesBelowConfig.temperature.immersionWetnessPerSecond.get() * Consts.SecondsPerTick
    } else if (level.isRainingAt(pos)) {
      CasualtiesBelowConfig.temperature.rainWetnessPerSecond
        .get() * Consts.SecondsPerTick + sweatPerSecond * Consts.SecondsPerTick
    } else {
      val dryingBonus = DryingBonusCallback.EVENT.invoker().dryingBonus(player)
      -CasualtiesBelowConfig.temperature.dryingCurveFormula.evaluate(apparent + dryingBonus) *
        airDryness * Consts.SecondsPerTick + sweatPerSecond * Consts.SecondsPerTick
    }
  }

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
