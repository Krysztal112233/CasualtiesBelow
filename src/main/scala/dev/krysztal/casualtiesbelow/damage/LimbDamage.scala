package dev.krysztal.casualtiesbelow.damage

import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

import dev.krysztal.casualtiesbelow.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.event.LimbInjuryCallback
import dev.krysztal.casualtiesbelow.api.event.LimbInjuryContext
import dev.krysztal.casualtiesbelow.component.BodyComponent
import dev.krysztal.casualtiesbelow.component.BodyPart
import dev.krysztal.casualtiesbelow.component.LimbCondition
import dev.krysztal.casualtiesbelow.component.LimbStats
import dev.krysztal.casualtiesbelow.pain.PainCalc

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents

/** Attributes incoming damage to body parts.
  *
  * Fall damage is attributed in [[onFallDamage]] (hooked from `LivingEntity.causeFallDamage`, where
  * the impact context — fall distance, damage modifier, formula output — is available).
  *
  * TODO: [[afterDamage]] sees them via Fabric's [[ServerLivingEntityEvents.AFTER_DAMAGE]], but
  * vanilla's [[DamageSource]] carries no hit-location information, so the rules for picking the
  * affected part are still to be decided.
  */
object LimbDamage {

  def register(): Unit = {
    ServerLivingEntityEvents.AFTER_DAMAGE.register(afterDamage)
  }

  /** Invoked from `LivingEntity.causeFallDamage` (see `LivingEntityMixin`). Attributes fall damage
    * to the legs when the impact actually dealt damage (`damaged` is the method's return value:
    * false for creative flight, slime-block landings, zero-damage falls, etc.).
    *
    * Server-side and players only: the body component exists on players and is authoritative on the
    * server.
    *
    * Note: `fallDistance` here is the raw method argument; `causeFallDamage` internally shortens it
    * during post-impulse grace (wind charges, mace smashes). In that edge case the limb attribution
    * slightly overshoots — accepted for now.
    */
  def onFallDamage(
      entity: LivingEntity,
      fallDistance: Double,
      damageModifier: Float,
      source: DamageSource,
      damaged: Boolean
  ): Unit = {
    val isEligibleFall = damaged && isFallDamage(source) &&
      entity.isInstanceOf[Player] && entity.level().isInstanceOf[ServerLevel]
    if (!isEligibleFall) return

    val player = entity.asInstanceOf[Player]

    // Players always use the custom formula (see FallDamageFormula.appliesTo), so the
    // severity can be recomputed deterministically from the same inputs.
    val damage = FallDamageFormula
      .calculateCustom(player, fallDistance, damageModifier)
      .toDouble
    if (damage > 0.0) {
      attributeFallDamage(player, source, damage)
      CasualtiesBelowComponents.Body.sync(player)
    }

  }

  /** Invoked after a living entity takes damage, before armor/enchantment reduction, and only when
    * the entity survives. Server-side only (`LivingEntity.hurtServer`).
    *
    * TODO: attribute non-fall damage to a body part (muscle health, skin integrity, bleeding, pain)
    * once the hit-location rules are decided. Fall damage is skipped here: it is already attributed
    * with richer context in [[onFallDamage]].
    */
  private val afterDamage: ServerLivingEntityEvents.AfterDamage =
    (entity, source, baseDamageTaken, damageTaken, blocked) => {
      if (isFallDamage(source)) {
        () // Already handled by onFallDamage.
      }
      // TODO: non-fall damage attribution.
    }

  /** Whether the source is fall damage. Vanilla's `IS_FALL` tag also covers ender pearls and
    * stalagmites.
    */
  def isFallDamage(source: DamageSource): Boolean =
    source.is(DamageTypeTags.IS_FALL)

  /** Maps fall damage (in half-hearts, as produced by [[FallDamageFormula]]) to leg injuries:
    *
    *   - any damage: both legs lose muscle health and gain pain
    *   - ≥ [[ScrapeThreshold]]: skin scrape, bleeding once skin integrity reaches zero
    *   - ≥ [[DislocationThreshold]]: one random leg is dislocated
    *   - ≥ [[FractureThreshold]]: one random leg fractures instead, with recovery time scaling with
    *     the damage
    *
    * The constants are balancing placeholders; expect them to become config values or EvalEx
    * formulas (like [[FallDamageFormula]]) once playtesting starts.
    */
  private def attributeFallDamage(player: Player, source: DamageSource, damage: Double): Unit = {
    val body = CasualtiesBelowComponents.Body.get(player)
    val severeLeg =
      if (player.getRandom.nextBoolean()) BodyPart.LegLeft else BodyPart.LegRight

    // General impact on both legs, then the severe injury on the randomly picked leg — the severe
    // leg effectively suffers two injuries, each cancellable on its own.
    BodyPart.Legs.foreach { leg =>
      applyFallInjury(body, player, leg, source, damage)
    }
    applySevereFallInjury(body, player, severeLeg, source, damage)
  }

  /** Applies the general fall impact (muscle health, impact pain, skin scrape, bleeding) to one
    * leg; cancelled injuries (see [[LimbInjuryCallback.EVENT]]) return early. Impact pain comes
    * from [[PainCalc.onFall]] via the context and can be adjusted by listeners.
    */
  private def applyFallInjury(
      body: BodyComponent,
      player: Player,
      leg: BodyPart,
      source: DamageSource,
      damage: Double
  ): Unit = {
    val context =
      LimbInjuryContext(player, leg, source, damage, None, PainCalc.onFall(damage))
    if (!LimbInjuryCallback.EVENT.invoker().onLimbInjury(context)) return

    val stats = body.stats(leg).copy()
    stats.muscleHealth = (stats.muscleHealth - damage * MuscleDamagePerPoint).max(0.0)
    stats.pain = (stats.pain + context.pain).min(LimbStats.MaxValue)

    if (damage >= ScrapeThreshold) {
      stats.skinIntegrity =
        (stats.skinIntegrity - (damage - ScrapeThreshold) * ScrapePerPoint).max(0.0)
      if (stats.skinIntegrity <= 0.0) {
        stats.externalBleedingRate += BleedingRateOnSkinBreak
      }
    }

    body.setStats(leg, stats)
  }

  /** Applies the severe fall injury (fracture above [[FractureThreshold]], dislocation above
    * [[DislocationThreshold]]) to the picked leg; cancelled injuries return early.
    *
    * Fracture and dislocation are discrete condition onsets: each fires the injury event with its
    * [[LimbCondition]] set and grants a fixed one-time pain injection (see
    * [[PainCalc.onConditionOnset]]), independent of the continuous impact pain from
    * [[applyFallInjury]]. Onset guards prevent re-granting: an already-fractured leg takes no new
    * condition, and an already-dislocated leg is not re-dislocated (a dislocated leg can still
    * progress to a fracture).
    */
  private def applySevereFallInjury(
      body: BodyComponent,
      player: Player,
      leg: BodyPart,
      source: DamageSource,
      damage: Double
  ): Unit = {
    if (damage < DislocationThreshold) return

    val current = body.stats(leg)
    if (current.fractureRecoveryTicks.isDefined) return

    val condition =
      if (damage >= FractureThreshold) {
        LimbCondition.Fracture
      } else {
        if (current.dislocated) return
        LimbCondition.Dislocation
      }

    val context = LimbInjuryContext(
      player,
      leg,
      source,
      damage,
      Some(condition),
      PainCalc.onConditionOnset(condition)
    )
    if (!LimbInjuryCallback.EVENT.invoker().onLimbInjury(context)) return

    val stats = current.copy()
    stats.pain = (stats.pain + context.pain).min(LimbStats.MaxValue)
    if (condition == LimbCondition.Fracture) {
      stats.fractureRecoveryTicks = Some(
        (FractureBaseRecoveryTicks * damage / FractureThreshold).toInt
      )
    } else {
      stats.dislocated = true
    }

    body.setStats(leg, stats)
  }

  /** Muscle health lost per half-heart of fall damage. */
  private val MuscleDamagePerPoint = 4.0

  /** Fall damage (half-hearts) at which the landing also scrapes the skin. */
  private val ScrapeThreshold = 6.0

  /** Skin integrity lost per half-heart above [[ScrapeThreshold]]. */
  private val ScrapePerPoint = 2.0

  /** Fall damage at which one random leg is dislocated. */
  private val DislocationThreshold = 8.0

  /** Fall damage at which one random leg fractures (instead of dislocating). */
  private val FractureThreshold = 10.0

  /** Fracture recovery time at exactly [[FractureThreshold]] damage, in ticks (~1 day); scales
    * linearly with the damage.
    */
  private val FractureBaseRecoveryTicks = 24000

  /** External bleeding rate (mL/tick) added when a scrape reduces skin integrity to zero. */
  private val BleedingRateOnSkinBreak = 0.5
}
