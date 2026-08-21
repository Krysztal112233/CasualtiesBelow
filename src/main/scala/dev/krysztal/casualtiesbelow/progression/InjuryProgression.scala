package dev.krysztal.casualtiesbelow.progression

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.LimbInjuries
import dev.krysztal.casualtiesbelow.bleeding.BleedingCalc
import dev.krysztal.casualtiesbelow.component.BodyPart
import dev.krysztal.casualtiesbelow.component.LimbStats
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.damage.CasualtiesBelowDamageTypes

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

/** Time evolution of injuries: what heals, what worsens, and what kills when left alone.
  *
  * Per server tick and per player (skipped in creative/spectator):
  *
  *   - fractures count down their recovery time and heal when it runs out; walking on a fractured
  *     or dislocated leg strains it, granting pain scaled by the leg's tissue damage (muscle and
  *     skin) at the configured rate
  *   - bleeding drains the blood volume and clots linearly; the per-limb rate is capped
  *     proportionally to the skin damage (see [[BleedingCalc.cap]]); reaching zero blood is fatal
  *     ([[CasualtiesBelowDamageTypes.BloodLoss]])
  *   - skin regrows only once the wound has clotted shut; muscle regrows regardless (slower)
  *   - pain decays linearly at the configured rate
  *
  * Dislocations never self-heal — they need treatment (not yet implemented).
  *
  * Healing is not an injury: it bypasses `LimbInjuries.apply` (no event, no jitter) and mutates the
  * components directly. Sync is throttled: continuous changes (decay/regen/blood drain) are flushed
  * once per [[SyncIntervalTicks]], discrete transitions (fracture healed, bleeding stopped) flush
  * immediately. Registration order matters: this pipeline must run before `LimbInjuries.register`'s
  * flush so its dirty marks ship in the same tick.
  *
  * The remaining numeric constants are balancing placeholders; expect them to become config values
  * once playtesting starts.
  */
object InjuryProgression {

  def register(): Unit = {
    ServerTickEvents.END_SERVER_TICK.register(tickServer)
    // Components persist through death (RespawnCopyStrategy.ALWAYS_COPY); without a refill a
    // bled-out player would die again on the spot after respawning.
    ServerPlayerEvents.AFTER_RESPAWN.register { (_, newPlayer, _) =>
      CasualtiesBelowComponents.Vitals.get(newPlayer).bloodVolume =
        CasualtiesBelowConfig.MaxBloodVolume.get()
    }
  }

  private def tickServer(server: MinecraftServer): Unit = {
    ticks += 1
    val syncTick = ticks % SyncIntervalTicks == 0
    server.getPlayerList.getPlayers.forEach { player =>
      tickPlayer(player, syncTick)
    }
  }

  private def tickPlayer(player: ServerPlayer, syncTick: Boolean): Unit = {
    if (player.isCreative || player.isSpectator || !player.isAlive) return

    val body = CasualtiesBelowComponents.Body.get(player)
    val walking = isWalking(player)
    var totalBleeding = 0.0

    BodyPart.values.foreach { part =>
      val current = body.stats(part)
      val updated = current.copy()
      val discrete = tickLimb(updated, walkingStrainRate(part, current, walking))
      totalBleeding += updated.externalBleedingRate
      if (updated != current) {
        body.setStats(part, updated)
        if (discrete || syncTick) {
          LimbInjuries.markDirty(player)
        }
      }
    }

    if (totalBleeding > 0.0) {
      val vitals = CasualtiesBelowComponents.Vitals.get(player)
      vitals.bloodVolume = (vitals.bloodVolume - totalBleeding).max(0.0)

      // Sync on every draining tick, not just SyncIntervalTicks boundaries: clotting can stop the
      // bleeding between two periodic syncs, and a skipped final value would only reach the client
      // when the player bleeds again.
      CasualtiesBelowComponents.Vitals.sync(player)

      if (vitals.bloodVolume <= 0.0) {
        player.hurtServer(
          player.level(),
          CasualtiesBelowDamageTypes.bloodLoss(player.level()),
          Float.MaxValue
        )
      }
    }
  }

  /** One tick of evolution for one limb, mutating the given copy in place. `strainPainRate` is the
    * walking-strain pain rate when the limb is a fractured/dislocated leg currently bearing the
    * walking player, zero otherwise. Returns whether a discrete transition occurred (fracture
    * healed, bleeding stopped).
    *
    * Ordering matters: bleeding clots before skin regrowth is considered, so a wound that seals
    * this tick starts regrowing skin immediately.
    */
  private def tickLimb(stats: LimbStats, strainPainRate: Double): Boolean = {
    val fractureHealed = tickFracture(stats)
    val bleedingStopped = tickBleeding(stats)
    tickSkinRegen(stats)
    tickMuscleRegen(stats)
    tickPainDecay(stats)
    tickWalkingStrain(stats, strainPainRate)
    fractureHealed || bleedingStopped
  }

  /** Counts down the fracture recovery time; returns true when the fracture healed this tick. */
  private def tickFracture(stats: LimbStats): Boolean = {
    if (stats.fractureRecoveryTicks.isEmpty) return false

    val remaining = stats.fractureRecoveryTicks.get
    if (remaining <= 1) {
      stats.fractureRecoveryTicks = None
      true
    } else {
      stats.fractureRecoveryTicks = Some(remaining - 1)
      false
    }
  }

  /** Clots an actively bleeding wound linearly (rate capped by the skin damage); returns true when
    * the bleeding stopped this tick.
    */
  private def tickBleeding(stats: LimbStats): Boolean = {
    if (stats.externalBleedingRate <= 0.0) return false

    val capped = stats.externalBleedingRate.min(BleedingCalc.cap(stats.skinIntegrity))
    val clotted = (capped - CasualtiesBelowConfig.ClottingRatePerTick.get()).max(0.0)
    stats.externalBleedingRate = clotted
    clotted == 0.0
  }

  /** Skin regrows only once the wound has clotted shut. */
  private def tickSkinRegen(stats: LimbStats): Unit = {
    if (stats.externalBleedingRate > 0.0) return
    if (stats.skinIntegrity >= LimbStats.MaxValue) return

    stats.skinIntegrity = (stats.skinIntegrity + SkinRegenPerTick).min(LimbStats.MaxValue)
  }

  /** Muscle regrows regardless of bleeding (slower than skin). */
  private def tickMuscleRegen(stats: LimbStats): Unit = {
    if (stats.muscleHealth >= LimbStats.MaxValue) return

    stats.muscleHealth = (stats.muscleHealth + MuscleRegenPerTick).min(LimbStats.MaxValue)
  }

  /** Pain decays linearly at the configured rate. */
  private def tickPainDecay(stats: LimbStats): Unit = {
    if (stats.pain <= 0.0) return

    stats.pain = (stats.pain - CasualtiesBelowConfig.PainDecayPerTick.get()).max(0.0)
  }

  /** Walking strain: pain scaled by the leg's tissue damage (muscle and skin), at the configured
    * rate for the leg's condition; a rate of zero means no strain applies this tick.
    */
  private def tickWalkingStrain(stats: LimbStats, strainPainRate: Double): Unit = {
    if (strainPainRate <= 0.0) return

    val tissueDamage =
      (2.0 - stats.muscleHealth / LimbStats.MaxValue -
        stats.skinIntegrity / LimbStats.MaxValue) / 2.0
    if (tissueDamage > 0.0) {
      stats.pain = (stats.pain + strainPainRate * tissueDamage).min(LimbStats.MaxValue)
    }
  }

  /** Pain rate for walking strain on the given limb: zero unless a leg bears the walking player.
    * Fracture and dislocation are independent conditions and their configured rates stack, so a leg
    * with both suffers both rates at once.
    */
  private def walkingStrainRate(part: BodyPart, stats: LimbStats, walking: Boolean): Double = {
    if (!walking || !BodyPart.Legs.contains(part)) return 0.0

    val fractureRate: Double =
      if (stats.fractureRecoveryTicks.isDefined) {
        CasualtiesBelowConfig.FracturedWalkingPainPerTick.get()
      } else {
        0.0
      }
    val dislocationRate: Double =
      if (stats.dislocated) CasualtiesBelowConfig.DislocatedWalkingPainPerTick.get() else 0.0
    fractureRate + dislocationRate
  }

  /** Whether the player is trying to walk on the ground this tick (walking, sprinting, sneaking —
    * not swimming, flying, or being passively pushed). Uses vanilla's last client input packet
    * rather than measured displacement, so pressing a move key while stuck against a wall still
    * counts as bearing weight, while water/knockback movement without input does not.
    */
  private def isWalking(player: ServerPlayer): Boolean = {
    if (!player.onGround()) return false

    val input = player.getLastClientInput
    input.forward() || input.backward() || input.left() || input.right()
  }

  private var ticks = 0

  /** Ticks between throttled syncs of continuous changes (20 = once per second). */
  private val SyncIntervalTicks = 20

  /** Skin integrity regrown per tick once the wound is sealed (100 over ~17 min). */
  private val SkinRegenPerTick = 0.005

  /** Muscle health regrown per tick (100 over ~33 min). */
  private val MuscleRegenPerTick = 0.0025

}
