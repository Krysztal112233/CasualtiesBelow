package dev.krysztal.casualtiesbelow.physiology.dirtiness

import java.util.UUID

import scala.collection.mutable

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BiomeTags
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.LayeredCauldronBlock
import net.minecraft.world.level.block.state.BlockState

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowTags
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*

/** Dirtiness: the whole-body hygiene axis. The environment and the player's own actions push it up;
  * only water washes it down. One-way feedback by design: physiological states (infection, pain,
  * bleeding) never push dirtiness back up, so no loop can spiral — any hole can be escaped by
  * washing once.
  *
  * Per server tick and per player (skipped in creative/spectator, like the rest of physiology):
  *
  *   - passive accrual runs at a fixed per-second base rate, scaled by the configured dirt gain
  *     multiplier and situational multipliers: sprinting, a full armor set (heat buildup), Nether
  *     biomes (ash, compounding vanilla's no-water rule) and sweating while the core runs hot
  *     (marked each tick by the temperature progression)
  *   - immersion in water washes at the configured rate, dampened in murky water (biome tag
  *     `casualtiesbelow:dirty_water`); rain is a slower free wash. Immersion wins over rain when
  *     both apply. Washing always far outruns accrual, so hygiene is a plannable resource rather
  *     than a constant nag
  *
  * Display bands exist only for client presentation; every mechanic computes from the raw value.
  * Event pulses (combat grime, digging dust, contaminated food) live in [[DirtinessSources]].
  */
object Dirtiness {

  def register(): Unit = {
    ServerTickEvents.END_SERVER_TICK.register { server =>
      try {
        server.getPlayerList.getPlayers.forEach { player =>
          tickPlayer(player)
        }
      } finally {
        // Sweat flags are set from the temperature progression while it ticks under
        // InjuryProgression's END_SERVER_TICK listener; read once per loop, then cleared. A flag
        // set after the loop in the same game tick waits one tick, which is harmless for a
        // per-second accrual multiplier.
        sweatingNow.clear()
      }
    }
    ServerPlayConnectionEvents.DISCONNECT.register { (handler, _) =>
      cauldronProgress.remove(handler.player.getUUID)
      ()
    }
  }

  /** Advances an isolated GameTest player that is not registered in the server player list. */
  private[casualtiesbelow] def tickForGameTest(player: ServerPlayer): Unit =
    tickPlayer(player)

  private def tickPlayer(player: ServerPlayer): Unit = {
    if (player.isCreative || player.isSpectator || !player.isAlive) {
      cauldronProgress.remove(player.getUUID)
      return
    }

    val vitals = player.vitals
    val level = player.level()
    val biome = level.getBiome(player.blockPosition())

    val accrual = accrualPerTick(
      Consts.Dirtiness.AccrualPerSecond,
      Consts.Dirtiness.SprintMultiplier,
      Consts.Dirtiness.ArmoredMultiplier,
      Consts.Dirtiness.NetherMultiplier,
      if (sweatingNow.contains(player.getUUID)) {
        Consts.Temperature.SweatDirtinessMultiplier
      } else {
        1.0
      },
      sprinting = player.isSprinting,
      fullyArmored = isFullyArmored(player),
      inNether = biome.is(BiomeTags.IS_NETHER)
    )
    val dirtGain = accrual * CasualtiesBelowConfig.diseaseHygiene.dirtAccumulationMultiplier.get()
    val wash = washPerTick(
      CasualtiesBelowConfig.diseaseHygiene.washWaterPerSecond.get(),
      Consts.Dirtiness.WashRainPerSecond,
      Consts.Dirtiness.DirtyWaterWashMultiplier,
      inWater = player.isInWater,
      inRain = level.isRainingAt(player.blockPosition()),
      murkyWater = biome.is(CasualtiesBelowTags.DirtyWaterBiomes)
    )
    val next =
      (vitals.dirtiness + dirtGain - wash)
        .max(0.0)
        .min(Consts.Dirtiness.MaxValue)
    vitals.setDirtiness(next)
  }

  /** Marks the player as sweating this tick so the hygiene accrual picks it up. Called by the
    * temperature progression only while sweat is actually produced (hot core plus active exertion);
    * flags are read and cleared by this object's own END_SERVER_TICK loop, so the contract is
    * at-most-one-tick staleness and no cleanup on disconnect is needed.
    */
  private[casualtiesbelow] def markSweating(id: UUID): Unit = {
    sweatingNow += id
    ()
  }

  /** Passive accrual for one tick: the per-second base with situational multipliers stacked
    * multiplicatively.
    */
  private[dirtiness] def accrualPerTick(
      basePerSecond: Double,
      sprintMultiplier: Double,
      armoredMultiplier: Double,
      netherMultiplier: Double,
      sweatMultiplier: Double,
      sprinting: Boolean,
      fullyArmored: Boolean,
      inNether: Boolean
  ): Double = {
    val situational =
      (if (sprinting) sprintMultiplier else 1.0) *
        (if (fullyArmored) armoredMultiplier else 1.0) *
        (if (inNether) netherMultiplier else 1.0) *
        sweatMultiplier
    basePerSecond.max(0.0) * situational / Consts.TicksPerSecond
  }

  /** Wash for one tick: immersion wins over rain when both apply; murky water dampens immersion.
    */
  private[dirtiness] def washPerTick(
      waterPerSecond: Double,
      rainPerSecond: Double,
      murkyMultiplier: Double,
      inWater: Boolean,
      inRain: Boolean,
      murkyWater: Boolean
  ): Double = {
    val perSecond =
      if (inWater) waterPerSecond.max(0.0) * (if (murkyWater) murkyMultiplier else 1.0)
      else if (inRain) rainPerSecond.max(0.0)
      else 0.0
    perSecond / Consts.TicksPerSecond
  }

  /** Washes a player standing in a water cauldron at the immersion rate (cauldron water is clean,
    * never murky), consuming one level per `cauldronPointsPerLevel` washed. Partial progress is
    * per-player and evaporates when the player leaves, dies or disconnects. No sync flush here: the
    * per-tick loop ships the changing value on its own cadence.
    */
  def onCauldronSoak(
      player: ServerPlayer,
      state: BlockState,
      level: Level,
      pos: BlockPos
  ): Unit = {
    if (player.isCreative || player.isSpectator || !player.isAlive) return

    val vitals = player.vitals
    val current = vitals.dirtiness
    if (current <= 0.0) {
      cauldronProgress.remove(player.getUUID)
      return
    }

    val washed =
      (CasualtiesBelowConfig.diseaseHygiene.washWaterPerSecond
        .get()
        .doubleValue
        .max(0.0) / Consts.TicksPerSecond)
        .min(current)
    if (washed <= 0.0) return
    vitals.setDirtiness(current - washed)

    val pointsPerLevel = Consts.Dirtiness.CauldronPointsPerLevel
    if (pointsPerLevel <= 0.0) return // Guard against an invalid fixed balance constant.
    val id = player.getUUID
    val progress = cauldronProgress.getOrElse(id, 0.0) + washed
    if (progress >= pointsPerLevel) {
      cauldronProgress.update(id, progress - pointsPerLevel)
      LayeredCauldronBlock.lowerFillLevel(state, level, pos)
    } else {
      cauldronProgress.update(id, progress)
    }
  }

  /** Wound infection chance multiplier: ramps linearly from 1 when clean to `1 + atMax` at maximum
    * dirtiness.
    */
  private[casualtiesbelow] def infectionChanceMultiplier(
      dirtiness: Double,
      maxDirtiness: Double,
      atMax: Double
  ): Double = 1.0 + atMax.max(0.0) * fraction(dirtiness, maxDirtiness)

  /** Skin regrowth multiplier: ramps linearly from 1 when clean to `minMultiplier` at maximum
    * dirtiness. Never zero on its own; stacks with the immune multiplier.
    */
  private[casualtiesbelow] def skinRegenMultiplier(
      dirtiness: Double,
      maxDirtiness: Double,
      minMultiplier: Double
  ): Double = 1.0 - (1.0 - minMultiplier.max(0.0).min(1.0)) * fraction(dirtiness, maxDirtiness)

  /** Continuous immune drain per tick: zero at or below the start dirtiness, ramping linearly to
    * `maxPerTick` at maximum dirtiness. A degenerate `maxDirtiness <= startDirtiness` disables the
    * drain entirely.
    */
  private[casualtiesbelow] def immuneDrainPerTick(
      dirtiness: Double,
      startDirtiness: Double,
      maxDirtiness: Double,
      maxPerTick: Double
  ): Double = {
    if (maxDirtiness <= startDirtiness || dirtiness <= startDirtiness) return 0.0
    val ramp = (dirtiness - startDirtiness) / (maxDirtiness - startDirtiness)
    maxPerTick.max(0.0) * ramp.min(1.0)
  }

  /** Food discomfort dose multiplier (eating with dirty hands): ramps linearly from 1 when clean to
    * `1 + atMax` at maximum dirtiness.
    */
  private[casualtiesbelow] def foodDiscomfortMultiplier(
      dirtiness: Double,
      maxDirtiness: Double,
      atMax: Double
  ): Double = 1.0 + atMax.max(0.0) * fraction(dirtiness, maxDirtiness)

  private def fraction(dirtiness: Double, maxDirtiness: Double): Double = {
    if (maxDirtiness <= 0.0) 0.0 else (dirtiness / maxDirtiness).max(0.0).min(1.0)
  }

  private def isFullyArmored(player: ServerPlayer): Boolean = {
    Consts.ArmorSlots.forall(slot => !player.getItemBySlot(slot).isEmpty)
  }

  private val cauldronProgress = mutable.HashMap.empty[UUID, Double]
  private val sweatingNow = mutable.Set.empty[UUID]
}
