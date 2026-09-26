package dev.krysztal.casualtiesbelow.physiology.temperature

import java.util.UUID

import scala.collection.mutable

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.LivingEntity

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowTags
import dev.krysztal.casualtiesbelow.api.event.BodyHeatContributionCallback
import dev.krysztal.casualtiesbelow.api.event.BodyHeatContributionContext
import dev.krysztal.casualtiesbelow.internal.Consts

/** Direct-contact heat driven by vanilla damage types — no entity-state scanning, and no heat
  * sustained between events. The damage pipeline is the sole authority for fire contact:
  * `hurtServer` is invoked per tick while touching lava, fire, a magma block or a campfire, and the
  * entry-layer event ([ServerLivingEntityEvents.ALLOW_DAMAGE]) is injected before invulnerability
  * throttling, so every real attempt is observed. The burning DOT (`on_fire`) jumps every 20 ticks
  * and is suppressed inside lava — its dose simply carries a full 20-tick interval, so the vanilla
  * rhythm itself provides the averaging. A fire-resistance effect cancels fire damage before the
  * entry layer — accepted as "no damage, no heat". `sulfur_cube_hot` (a transient melee pulse) is
  * deliberately not mapped.
  *
  * Each qualifying hit converts to a dose sized by its emission rhythm and accumulates in
  * [pending]; [contribute] drains the accumulation in one tick through the direct channel, with the
  * frame's fire resistance applied at the source.
  */
private[temperature] object HeatDamageContribution extends BodyHeatContributionCallback {

  /** Registers both ends of the pipeline: the damage-entry writer and the per-tick reader. */
  def register(): Unit = {
    ServerLivingEntityEvents.ALLOW_DAMAGE.register(onIncomingDamage)
    BodyHeatContributionCallback.EVENT.register(this)
  }

  /** Drops a player's pending doses when progression is skipped or the player leaves. */
  def discard(id: UUID): Unit = pending.remove(id)

  override def contribute(
      player: ServerPlayer,
      frame: BodyHeatContributionCallback.Frame,
      context: BodyHeatContributionContext
  ): Unit = {
    pending.remove(player.getUUID).foreach { dose =>
      context.addDirect(dose * (1.0 - frame.fireResistance))
    }
  }

  private val pending = mutable.Map.empty[UUID, Double]

  private def onIncomingDamage(
      entity: LivingEntity,
      source: DamageSource,
      amount: Float
  ): Boolean = {
    if (!entity.isInstanceOf[ServerPlayer]) return true
    val player = entity.asInstanceOf[ServerPlayer]

    tierPerMinute(source).foreach { rate =>
      // Deliver the heat the vanilla rhythm owes: the burning DOT jumps every 20 ticks, so its
      // dose carries a full 20-tick interval; contact sources fire per tick. Averaged over time
      // each tier lands exactly on its configured rate — nothing is sustained between events.
      val intervalTicks = if (source.is(DamageTypes.ON_FIRE)) 20.0 else 1.0
      pending(player.getUUID) = pending.getOrElse(player.getUUID, 0.0) + rate * intervalTicks
    }
    true // the listener observes; it never vetoes damage
  }

  /** The tier table in priority order — first matching tag wins, and datapacks extend any tier by
    * adding damage types to its tag.
    */
  private val Tiers = List(
    (CasualtiesBelowTags.DamageTypes.HeatExtreme, Consts.Temperature.HeatExtremePerMinute),
    (CasualtiesBelowTags.DamageTypes.HeatStrong, Consts.Temperature.HeatStrongPerMinute),
    (CasualtiesBelowTags.DamageTypes.HeatNormal, Consts.Temperature.HeatNormalPerMinute)
  )

  private def tierPerMinute(source: DamageSource): Option[Double] =
    Tiers.collectFirst { case (tag, rate) if source.is(tag) => rate }
}
