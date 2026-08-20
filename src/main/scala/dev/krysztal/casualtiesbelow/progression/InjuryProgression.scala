package dev.krysztal.casualtiesbelow.progression

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.LimbInjuries
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
  *   - fractures count down their recovery time and heal when it runs out
  *   - bleeding drains the blood volume and clots linearly; the per-limb rate is capped
  *     proportionally to the skin damage (see [[bleedingCap]]); reaching zero blood is fatal
  *     ([[CasualtiesBelowDamageTypes.BloodLoss]])
  *   - skin regrows only once the wound has clotted shut; muscle regrows regardless (slower)
  *   - pain decays linearly
  *
  * Dislocations never self-heal — they need treatment (not yet implemented).
  *
  * Healing is not an injury: it bypasses `LimbInjuries.apply` (no event, no jitter) and mutates the
  * components directly. Sync is throttled: continuous changes (decay/regen/blood drain) are flushed
  * once per [[SyncIntervalTicks]], discrete transitions (fracture healed, bleeding stopped) flush
  * immediately. Registration order matters: this pipeline must run before `LimbInjuries.register`'s
  * flush so its dirty marks ship in the same tick.
  *
  * The numeric constants other than the bleeding ones are balancing placeholders; expect them to
  * become config values once playtesting starts.
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
    var totalBleeding = 0.0

    BodyPart.values.foreach { part =>
      val current = body.stats(part)
      val (updated, discrete) = tickLimb(current.copy())
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
      if (syncTick) {
        CasualtiesBelowComponents.Vitals.sync(player)
      }
      if (vitals.bloodVolume <= 0.0) {
        player.hurtServer(
          player.level(),
          CasualtiesBelowDamageTypes.bloodLoss(player.level()),
          Float.MaxValue
        )
      }
    }
  }

  /** One tick of evolution for one limb, mutating the given copy in place. Returns it together with
    * whether a discrete transition occurred (fracture healed, bleeding stopped).
    */
  private def tickLimb(stats: LimbStats): (LimbStats, Boolean) = {
    var discrete = false

    stats.fractureRecoveryTicks.foreach { remaining =>
      if (remaining <= 1) {
        stats.fractureRecoveryTicks = None
        discrete = true
      } else {
        stats.fractureRecoveryTicks = Some(remaining - 1)
      }
    }

    if (stats.externalBleedingRate > 0.0) {
      val capped = stats.externalBleedingRate.min(bleedingCap(stats.skinIntegrity))
      val clotted = (capped - CasualtiesBelowConfig.ClottingRatePerTick.get()).max(0.0)
      if (clotted == 0.0) {
        stats.externalBleedingRate = 0.0
        discrete = true
      } else {
        stats.externalBleedingRate = clotted
      }
    }

    // Skin regrows only once the wound has clotted shut; muscle regrows regardless.
    if (stats.externalBleedingRate == 0.0 && stats.skinIntegrity < LimbStats.MaxValue) {
      stats.skinIntegrity = (stats.skinIntegrity + SkinRegenPerTick).min(LimbStats.MaxValue)
    }
    if (stats.muscleHealth < LimbStats.MaxValue) {
      stats.muscleHealth = (stats.muscleHealth + MuscleRegenPerTick).min(LimbStats.MaxValue)
    }
    if (stats.pain > 0.0) {
      stats.pain = (stats.pain - PainDecayPerTick).max(0.0)
    }

    (stats, discrete)
  }

  private var ticks = 0

  /** Ticks between throttled syncs of continuous changes (20 = once per second). */
  private val SyncIntervalTicks = 20

  /** Pain lost per tick (1.0/s: a full limb's pain fades in ~100 s). */
  private val PainDecayPerTick = 0.05

  /** Skin integrity regrown per tick once the wound is sealed (100 over ~8 min). */
  private val SkinRegenPerTick = 0.01

  /** Muscle health regrown per tick (100 over ~17 min). */
  private val MuscleRegenPerTick = 0.005

  /** The most a limb can bleed given its skin state: linear in the skin damage, from zero on intact
    * skin up to `MaxExternalBleedingRate` on fully destroyed skin.
    */
  private def bleedingCap(skinIntegrity: Double): Double =
    CasualtiesBelowConfig.MaxExternalBleedingRate.get() *
      (1.0 - skinIntegrity / LimbStats.MaxValue)
}
