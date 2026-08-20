package dev.krysztal.casualtiesbelow.damage

import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.DamageTypeTags
import net.minecraft.tags.ItemTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Projectile

import dev.krysztal.casualtiesbelow.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.LimbInjuries
import dev.krysztal.casualtiesbelow.bleeding.BleedingCalc
import dev.krysztal.casualtiesbelow.component.BodyPart
import dev.krysztal.casualtiesbelow.component.LimbCondition
import dev.krysztal.casualtiesbelow.pain.PainCalc

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents

/** Attributes incoming damage to body parts.
  *
  * Fall damage is attributed in [[onFallDamage]] (hooked from `LivingEntity.causeFallDamage`, where
  * the impact context — fall distance, damage modifier, formula output — is available). Other
  * damage goes through [[afterDamage]] (Fabric's `ServerLivingEntityEvents.AFTER_DAMAGE`), which
  * carries no hit-location information: the affected part is guessed from hit geometry (see
  * [[HitLocation]]). Melee and projectile damage are attributed so far.
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
      // Sync is automatic: applied injuries mark the player dirty, flushed at tick end
      // (see LimbInjuries.register).
      attributeFallDamage(player, source, damage)
    }

  }

  /** Invoked after a living entity takes damage, before armor/enchantment reduction, and only when
    * the entity survives. Server-side only (`LivingEntity.hurtServer`).
    *
    * Fall damage is skipped here: it is already attributed with richer context in [[onFallDamage]].
    */
  private val afterDamage: ServerLivingEntityEvents.AfterDamage =
    (entity, source, _, damageTaken, blocked) => onAfterDamage(entity, source, damageTaken, blocked)

  private def onAfterDamage(
      entity: LivingEntity,
      source: DamageSource,
      damageTaken: Float,
      blocked: Boolean
  ): Unit = {
    if (!entity.isInstanceOf[Player]) return // The body component exists on players only.
    if (isFallDamage(source)) return
    if (blocked) return
    if (damageTaken <= 0) return

    val player = entity.asInstanceOf[Player]
    val damage = damageTaken.toDouble
    source match {
      case s if isMelee(s)      => attributeMeleeDamage(player, s, damage)
      case s if isProjectile(s) => attributeProjectileDamage(player, s, damage)
      case _                    => ()
    }
  }

  /** Whether the source is fall damage. Vanilla's `IS_FALL` tag also covers ender pearls and
    * stalagmites.
    */
  def isFallDamage(source: DamageSource): Boolean =
    source.is(DamageTypeTags.IS_FALL)

  /** Whether the source is a melee hit: direct damage dealt by a living attacker (mob attacks,
    * player attacks, stings, ...).
    */
  def isMelee(source: DamageSource): Boolean =
    source.isDirect && source.getDirectEntity.isInstanceOf[LivingEntity]

  /** Whether the source is a projectile hit: the direct entity is a projectile (arrows, tridents,
    * fireballs, shulker bullets, ...). Instance-based rather than tag-based, so it holds for every
    * projectile entity regardless of damage type.
    */
  def isProjectile(source: DamageSource): Boolean =
    source.getDirectEntity.isInstanceOf[Projectile]

  /** Maps melee damage (in half-hearts, post-shield/pre-armor) to limb injuries:
    *
    *   - any hit: the located part ([[HitLocation]]) loses muscle health and gains pain
    *     ([[PainCalc.onMelee]] via the injury context)
    *   - sharp weapons (swords/axes): the skin is also cut; the resulting bleeding is capped
    *     linearly by the post-hit skin integrity
    *
    * The constants are balancing placeholders, like the fall ones below.
    */
  private def attributeMeleeDamage(player: Player, source: DamageSource, damage: Double): Unit = {
    val part = HitLocation.pick(player, source)
    // TODO: distinguish weapon kinds with a dedicated item tag (e.g. `casualtiesbelow:sharp`)
    // instead of hardcoding the vanilla swords/axes tags.
    val isSharp = Option(source.getWeaponItem).exists { weapon =>
      weapon.is(ItemTags.SWORDS) || weapon.is(ItemTags.AXES)
    }

    LimbInjuries(player, part, source, damage, pain = PainCalc.onMelee(damage)) {
      (stats, effectiveDamage) =>
        stats.muscleHealth =
          (stats.muscleHealth - effectiveDamage * MeleeMuscleDamagePerPoint).max(0.0)

        if (isSharp) {
          BleedingCalc.applyWound(
            stats,
            effectiveDamage * SharpSkinDamagePerPoint,
            MeleeBleedingRatePerWound
          )
        }
    }
  }

  /** Maps projectile damage (in half-hearts, post-shield/pre-armor) to limb injuries: a piercing
    * wound on the located part ([[HitLocation]]) — the skin is always punctured, the muscle takes
    * the rest, and bleeding is capped linearly by the post-hit skin integrity. Pain:
    * [[PainCalc.onProjectile]] via the injury context.
    *
    * The constants are balancing placeholders, like the melee ones above.
    */
  private def attributeProjectileDamage(
      player: Player,
      source: DamageSource,
      damage: Double
  ): Unit = {
    val part = HitLocation.pick(player, source)

    LimbInjuries(player, part, source, damage, pain = PainCalc.onProjectile(damage)) {
      (stats, effectiveDamage) =>
        BleedingCalc.applyWound(
          stats,
          effectiveDamage * ProjectileSkinDamagePerPoint,
          ProjectileBleedingRatePerWound
        )
        stats.muscleHealth =
          (stats.muscleHealth - effectiveDamage * ProjectileMuscleDamagePerPoint).max(0.0)
    }
  }

  /** Maps fall damage (in half-hearts, as produced by [[FallDamageFormula]]) to leg injuries:
    *
    *   - any damage: both legs lose muscle health and gain pain
    *   - ≥ [[ScrapeThreshold]]: skin scrape and external bleeding capped by skin damage
    *   - ≥ [[DislocationThreshold]]: one random leg is dislocated
    *   - ≥ [[FractureThreshold]]: one random leg fractures instead, with recovery time scaling with
    *     the damage
    *
    * The constants are balancing placeholders; expect them to become config values or EvalEx
    * formulas (like [[FallDamageFormula]]) once playtesting starts.
    */
  private def attributeFallDamage(player: Player, source: DamageSource, damage: Double): Unit = {
    val severeLeg =
      if (player.getRandom.nextBoolean()) BodyPart.LegLeft else BodyPart.LegRight

    // General impact on both legs, then the severe injury on the randomly picked leg — the severe
    // leg effectively suffers two injuries, each cancellable on its own.
    BodyPart.Legs.foreach { leg =>
      applyFallInjury(player, leg, source, damage)
    }
    applySevereFallInjury(player, severeLeg, source, damage)
  }

  /** Fall impact rules for one leg: muscle health, impact pain ([[PainCalc.onFall]] via the injury
    * context), skin scrape, and bleeding capped linearly by the post-impact skin integrity.
    * Application mechanics (context, event, commit) live in [[LimbInjuries.apply]].
    */
  private def applyFallInjury(
      player: Player,
      leg: BodyPart,
      source: DamageSource,
      damage: Double
  ): Unit = {
    LimbInjuries(player, leg, source, damage, pain = PainCalc.onFall(damage)) {
      (stats, effectiveDamage) =>
        stats.muscleHealth = (stats.muscleHealth - effectiveDamage * MuscleDamagePerPoint).max(0.0)

        if (effectiveDamage >= ScrapeThreshold) {
          BleedingCalc.applyWound(
            stats,
            (effectiveDamage - ScrapeThreshold) * ScrapePerPoint,
            FallBleedingRatePerWound
          )
        }
    }
  }

  /** Severe fall injury rules for the picked leg: fracture above [[FractureThreshold]], dislocation
    * above [[DislocationThreshold]] — discrete condition onsets carrying a fixed one-time pain
    * grant ([[PainCalc.onConditionOnset]]), independent of impact pain.
    *
    * Onset guards prevent re-granting: an already-fractured leg takes no new condition, and an
    * already-dislocated leg is not re-dislocated (a dislocated leg can still progress to a
    * fracture).
    */
  private def applySevereFallInjury(
      player: Player,
      leg: BodyPart,
      source: DamageSource,
      damage: Double
  ): Unit = {
    if (damage < DislocationThreshold) return

    val current = CasualtiesBelowComponents.Body.get(player).stats(leg)
    if (current.fractureRecoveryTicks.isDefined) return

    val condition =
      if (damage >= FractureThreshold) {
        LimbCondition.Fracture
      } else {
        if (current.dislocated) return
        LimbCondition.Dislocation
      }

    LimbInjuries(
      player,
      leg,
      source,
      damage,
      Some(condition),
      PainCalc.onConditionOnset(condition)
    ) { (stats, effectiveDamage) =>
      if (condition == LimbCondition.Fracture) {
        stats.fractureRecoveryTicks = Some(
          (FractureBaseRecoveryTicks * effectiveDamage / FractureThreshold).toInt
        )
      } else {
        stats.dislocated = true
      }
    }
  }

  /** Skin integrity lost per half-heart of projectile damage. */
  private val ProjectileSkinDamagePerPoint = 3.0

  /** Muscle health lost per half-heart of projectile damage. */
  private val ProjectileMuscleDamagePerPoint = 2.0

  /** External bleeding rate (mL/tick) granted by one projectile wound, before the skin cap. */
  private val ProjectileBleedingRatePerWound = 0.15

  /** Muscle health lost per half-heart of melee damage. */
  private val MeleeMuscleDamagePerPoint = 3.0

  /** Skin integrity lost per half-heart of sharp-weapon melee damage. */
  private val SharpSkinDamagePerPoint = 2.0

  /** External bleeding rate (mL/tick) granted by one sharp melee wound, before the skin cap. */
  private val MeleeBleedingRatePerWound = 0.2

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

  /** External bleeding rate (mL/tick) granted by one fall scrape, before the skin cap. */
  private val FallBleedingRatePerWound = 0.5
}
