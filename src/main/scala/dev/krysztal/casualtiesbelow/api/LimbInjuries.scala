package dev.krysztal.casualtiesbelow.api

import java.lang.Boolean
import java.util.Collections
import java.util.WeakHashMap

import net.minecraft.util.RandomSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.player.Player

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.LimbCondition
import dev.krysztal.casualtiesbelow.api.body.LimbStats
import dev.krysztal.casualtiesbelow.api.event.LimbInjuryCallback
import dev.krysztal.casualtiesbelow.api.event.LimbInjuryContext

/** Central application point for limb injuries — the single path through which an injury reaches a
  * limb, for the mod's own damage attribution and for other mods alike.
  *
  * Every application builds a [[LimbInjuryContext]], fires [[LimbInjuryCallback.EVENT]] (listeners
  * may cancel the injury or adjust its pain), applies the granted pain, then runs the caller's stat
  * mutation and commits. Damage sources only supply their own rules: which parts are hit, how
  * severe, and which stats change.
  *
  * Sync: applied injuries mark the player dirty and are flushed to clients once at the end of the
  * server tick (see [[register]]) — one packet per player per tick no matter how many injuries the
  * tick caused, and callers cannot forget to sync. Command-driven mutations sync immediately on
  * their own (instant feedback).
  */
object LimbInjuries {

  /** Players with unsynced injury changes. Weak keys: offline/unloaded players drop out naturally
    * (entities use identity equality).
    */
  private val DirtyPlayers =
    Collections.newSetFromMap(new WeakHashMap[Player, Boolean]())

  /** Registers the end-of-tick sync flush. Called once from the mod initializer. */
  def register(): Unit =
    ServerTickEvents.END_SERVER_TICK.register { _ =>
      if (!DirtyPlayers.isEmpty) {
        DirtyPlayers.forEach { player =>
          if (!player.isRemoved) {
            CasualtiesBelowComponents.Body.sync(player)
          }
        }
        DirtyPlayers.clear()
      }
    }

  /** Marks a player's body for the end-of-tick sync flush. Injuries applied through [[apply]] are
    * marked automatically; direct component mutations (e.g. `InjuryProgression`'s healing) must
    * call this themselves.
    */
  def markDirty(player: Player): Unit = DirtyPlayers.add(player)

  /** Applies one injury to one limb. Returns `false` when a listener cancelled it.
    *
    * @param pain
    *   pain granted by this injury (from `PainCalc`); applied capped at the limb maximum after the
    *   event, so listener adjustments take effect
    * @param jitter
    *   random fluctuation range applied to the injury's numbers: the damage is rolled once as
    *   `damage ± jitter` up front (listeners see the effective damage in the context), and the
    *   granted pain is rolled as `pain ± jitter` after listener adjustments. Both clamp at 0; 0
    *   disables fluctuation
    * @param mutate
    *   the injury's own stat changes (muscle health, conditions, bleeding, ...) applied to a copy
    *   of the limb's current stats; receives the effective (jitter-rolled) damage
    */
  def apply(
      player: Player,
      part: BodyPart,
      source: DamageSource,
      damage: Double,
      condition: Option[LimbCondition] = None,
      pain: Double = 0.0,
      jitter: Double = 3.0
  )(mutate: (LimbStats, Double) => Unit): Boolean = {
    val body = CasualtiesBelowComponents.Body.get(player)
    val random = player.getRandom

    val effectiveDamage = (damage + rollJitter(random, jitter)).max(0.0)
    val context = LimbInjuryContext(player, part, source, effectiveDamage, condition, pain)
    if (!LimbInjuryCallback.EVENT.invoker().onLimbInjury(context)) return false

    val grantedPain = (context.pain + rollJitter(random, jitter)).max(0.0)

    val stats = body.stats(part).copy()
    stats.pain = (stats.pain + grantedPain).min(LimbStats.MaxValue)
    mutate(stats, effectiveDamage)
    body.setStats(part, stats)
    DirtyPlayers.add(player)
    true
  }

  /** Uniform roll in `[-jitter, +jitter]`. */
  private def rollJitter(random: RandomSource, jitter: Double): Double =
    (random.nextDouble() * 2.0 - 1.0) * jitter
}
