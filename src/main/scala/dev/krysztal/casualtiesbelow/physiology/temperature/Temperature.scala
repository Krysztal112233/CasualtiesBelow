package dev.krysztal.casualtiesbelow.physiology.temperature

import java.lang.{Boolean => JBoolean}
import java.util.UUID

import scala.collection.mutable

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.level.block.state.properties.BlockStateProperties

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowTags
import dev.krysztal.casualtiesbelow.api.event.BodyHeatContributionCallback
import dev.krysztal.casualtiesbelow.api.event.BodyHeatContributionContext
import dev.krysztal.casualtiesbelow.api.event.DryingBonusCallback
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.BiomeClimateAccess
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataLookup
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataStores
import dev.krysztal.casualtiesbelow.internal.extension.BiomeExtensions.*
import dev.krysztal.casualtiesbelow.mixin.FoodDataAccessor
import dev.krysztal.casualtiesbelow.physiology.dirtiness.Dirtiness

/** Body temperature progression: the environment pulls the core temperature towards an equilibrium
  * while internal heat production acts on it directly.
  *
  * Every tick, per player (driven from [[InjuryProgression.tickPlayer]]):
  *
  *   1. the vanilla height-adjusted biome temperature maps to an apparent temperature (°C); while
  *      immersed, the water takes over with a 0°C floor for liquid water
  *   2. the comfort band maps the apparent temperature to an equilibrium core temperature: inside
  *      the band the equilibrium is normal body temperature (no drift), outside it deviates by the
  *      configured slope
  *   3. armor weakens the equilibrium's pull: each worn piece contributes its material's insulation
  *      and dissipation-block coefficients (data-driven, keyed by equipment asset id) weighted by
  *      body coverage; wetness collapses both coefficients together, so soaked armor neither keeps
  *      you warm nor keeps you stifled
  *   4. heat contributions arrive through [[BodyHeatContributionCallback]]: `addDirect` bypasses
  *      armor (exercise heat, fire/lava contact heat), `addDissipative` is scaled by the armor's
  *      surviving dissipation block (evaporative cooling)
  *   5. the core temperature approaches the armor-weakened equilibrium exponentially with the
  *      configured time constant (faster while immersed), plus the production terms
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
    BodyHeatContributionCallback.EVENT.register(ExerciseHeat)
    BodyHeatContributionCallback.EVENT.register(FireContactHeat)
    BodyHeatContributionCallback.EVENT.register(EvaporativeCooling)
    DryingBonusCallback.EVENT.register(FireDryingBonus)
    ServerPlayConnectionEvents.DISCONNECT.register { (handler, _) =>
      ExertionTracker.discard(handler.player.getUUID);
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

    // (a) Apparent temperature: biome-mapped; while immersed the water takes over and liquid
    // water never goes below freezing.
    val mapped = biome.mappedTemperature(pos, level.getSeaLevel)
    val immersed = player.isInWater
    val apparent = TemperatureCalc.apparentTemperature(mapped, immersed)

    // (b) Equilibrium core temperature from the comfort band.
    val equilibrium = CasualtiesBelowConfig.temperature.comfortBandFormula.evaluate(
      apparent,
      CasualtiesBelowConfig.temperature.comfortLowCelsius.get(),
      CasualtiesBelowConfig.temperature.comfortHighCelsius.get(),
      CasualtiesBelowConfig.temperature.comfortSlope.get()
    )

    // (c) Armor scan: coverage-weighted material coefficients over the four armor slots.
    val store = GameplayDataStores.server(level.getServer)
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

    // (d) Wetness collapses both armor coefficients together: soaked armor neither insulates
    // nor stifles. Fire resistance is the fire layer's own coefficient and does not collapse.
    val wetness = vitals.wetness
    val collapse = CasualtiesBelowConfig.temperature.wetnessCollapseFormula
      .evaluate(wetness)
      .max(0.0)
      .min(1.0)
    val effectiveInsulation = insulation * collapse
    val effectiveDissipationBlock = dissipationBlock * collapse

    // (e) Heat contributions. The frame hands this tick's armor/wetness context to the built-in
    // listeners; it is valid only for the synchronous dispatch below.
    val airDryness = 1.0 - BiomeClimateAccess.downfall(biome).toDouble
    frame = Some(TickFrame(fireResistance, wetness, airDryness))
    try {
      ContributionAccumulator.reset()
      BodyHeatContributionCallback.EVENT.invoker().contribute(player, ContributionAccumulator)
    } finally {
      frame = None
    }

    // (f) Exponential approach of the armor-weakened equilibrium plus production terms.
    val effectiveEquilibrium =
      CasualtiesBelowConfig.temperature.effectiveTemperatureFormula.evaluate(
        equilibrium,
        effectiveInsulation
      )
    val ratePerSecond = TemperatureCalc.approachRatePerSecond(
      CasualtiesBelowConfig.temperature.tauAirMinutes.get(),
      immersed,
      CasualtiesBelowConfig.temperature.immersionRateMultiplier.get()
    )
    val productionPerSecond = TemperatureCalc.productionPerSecond(
      ContributionAccumulator.directTotal,
      ContributionAccumulator.dissipativeTotal,
      effectiveDissipationBlock
    )
    val nextCore = TemperatureCalc.nextCoreTemperature(
      vitals.bodyTemperature,
      effectiveEquilibrium,
      ratePerSecond,
      productionPerSecond,
      Consts.SecondsPerTick
    )
    val coreChanged = VitalsMutations.setBodyTemperature(vitals, nextCore)

    // (g) Wetness axis: fast accrual while immersed, slow in rain, otherwise drying — with sweat
    // added on top of the non-immersion branches while the core runs hot.
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
    val deltaWetness =
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
    val nextWetness = TemperatureCalc.nextWetness(wetness, deltaWetness)
    val wetnessChanged = VitalsMutations.setWetness(vitals, nextWetness)

    // Throttle like Dirtiness: the approach never exactly converges, so core is always dirty;
    // contribute to the sync decision only on sync ticks (the state itself updates every tick).
    (coreChanged || wetnessChanged) && syncTick
  }

  /** Drops this player's exertion tracking state when progression is skipped (death,
    * creative/spectator).
    */
  private[casualtiesbelow] def discard(player: ServerPlayer): Unit =
    ExertionTracker.discard(player.getUUID)

  /** Per-tick context handed to the built-in contribution listeners during dispatch. */
  private final case class TickFrame(
      fireResistance: Double,
      wetness: Double,
      airDryness: Double
  )

  /** Valid only while [[BodyHeatContributionCallback]] is being dispatched from [[tick]]; the
    * firing side is the only producer, on the server thread.
    */
  private var frame: Option[TickFrame] = None

  /** Shared contribution accumulator: one allocation, reset before every dispatch. */
  private val ContributionAccumulator = new BodyHeatContributionCallback.Accumulator

  /** Vanilla sprinting accrues exhaustion at ~0.56/s; it anchors the exertion fraction for both
    * exercise heat and sweating.
    */
  private val SprintExhaustionPerSecond = 0.56

  /** Shared exertion signal: vanilla's exhaustion bookkeeping already prices each activity, so its
    * per-tick delta is the signal. The periodic 4.0 hunger-billing drain shows up as a negative
    * delta and is truncated (an accounting artifact, not negative exercise). The delta is smoothed
    * with an exponential moving average over roughly five seconds so single actions (a jump, an
    * attack) register as brief exertion instead of one-tick spikes. Used by the exercise-heat and
    * sweating listeners; [[discard]] clears a player's state.
    */
  private object ExertionTracker {

    /** Smoothed exhaustion rate in units per second; 0 before any exertion is observed. */
    def exhaustionPerSecond(id: UUID): Double =
      tracked.get(id).fold(0.0)(_.smoothedPerTick * Consts.TicksPerSecond)

    def observe(player: ServerPlayer): Double = {
      val exhaustion = player.getFoodData
        .asInstanceOf[FoodDataAccessor]
        .casualtiesbelow$getExhaustionLevel()
      val track = tracked.getOrElseUpdate(player.getUUID, new Track(exhaustion))
      val delta = (exhaustion - track.lastExhaustion).toDouble.max(0.0)
      track.lastExhaustion = exhaustion
      track.smoothedPerTick += (delta - track.smoothedPerTick) * SmoothingAlpha
      track.smoothedPerTick * Consts.TicksPerSecond
    }

    def discard(id: UUID): Unit = tracked.remove(id)

    private final class Track(var lastExhaustion: Float) {
      var smoothedPerTick: Double = 0.0
    }

    private val tracked = mutable.Map.empty[UUID, Track]

    /** EMA weight for a ~5 second (100 tick) window. */
    private val SmoothingAlpha = 2.0 / (100.0 + 1.0)
  }

  /** Exercise heat: the shared exertion signal converts to direct-channel heat at the configured
    * rate per exhaustion unit.
    */
  private object ExerciseHeat extends BodyHeatContributionCallback {

    override def contribute(player: ServerPlayer, context: BodyHeatContributionContext): Unit = {
      val exhaustionPerSecond = ExertionTracker.observe(player)
      if (exhaustionPerSecond > 0.0) {
        context.addDirect(
          exhaustionPerSecond * CasualtiesBelowConfig.temperature.exerciseHeatPerExhaustionPerSecond
            .get()
        )
      }
    }
  }

  /** Fire/lava contact heat: direct contact transfers heat regardless of insulation (convection
    * clothing cannot stop conduction), so it goes to the direct channel in three tiers — lava
    * contact, being on fire, standing on a heat-source block — highest tier wins, tiers never stack
    * (vanilla also suppresses the on-fire damage-over-time while in lava). The armor's
    * fire-resistance coefficient (netherite's "doesn't burn" extension) reduces the tier here, at
    * the source, as the event contract requires.
    */
  private object FireContactHeat extends BodyHeatContributionCallback {

    override def contribute(player: ServerPlayer, context: BodyHeatContributionContext): Unit = {
      val tier: Double =
        if (player.isInLava) {
          CasualtiesBelowConfig.temperature.lavaContactHeatPerMinute.get().doubleValue
        } else if (player.isOnFire) {
          CasualtiesBelowConfig.temperature.onFireHeatPerMinute.get().doubleValue
        } else if (standingOnHeatSource(player)) {
          CasualtiesBelowConfig.temperature.heatSourceBlockHeatPerMinute.get().doubleValue
        } else {
          0.0
        }
      if (tier > 0.0) {
        val resistance = frame.fold(0.0)(_.fireResistance)
        context.addDirect(tier * (1.0 - resistance))
      }
    }

    /** The block at the feet and the one below; tag members with a LIT property (campfires) only
      * count while lit.
      */
    private def standingOnHeatSource(player: ServerPlayer): Boolean = {
      val level = player.level()
      val feet = player.blockPosition()
      List(feet, feet.below()).exists { pos =>
        val state = level.getBlockState(pos)
        state.is(CasualtiesBelowTags.HeatSourceBlocks) &&
        (!state.hasProperty(BlockStateProperties.LIT) ||
          state.getValue[JBoolean](BlockStateProperties.LIT).booleanValue())
      }
    }
  }

  /** Evaporative cooling: wet skin sheds heat in proportion to wetness and air dryness (a jungle
    * defeats sweat, a desert exploits it). This is an active dissipation term, so it travels on the
    * dissipative channel and pays the armor's surviving dissipation block.
    */
  private object EvaporativeCooling extends BodyHeatContributionCallback {

    override def contribute(player: ServerPlayer, context: BodyHeatContributionContext): Unit = {
      frame.foreach { f =>
        if (f.wetness > 0.0) {
          context.addDissipative(
            f.wetness * f.airDryness * CasualtiesBelowConfig.temperature.evaporationCoolingPerMinute
              .get()
          )
        }
      }
    }
  }

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
