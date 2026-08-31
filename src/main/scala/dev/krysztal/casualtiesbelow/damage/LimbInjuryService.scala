package dev.krysztal.casualtiesbelow.damage

import scala.jdk.OptionConverters.*

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.RandomSource
import net.minecraft.world.damagesource.DamageSource

import dev.krysztal.casualtiesbelow.adrenaline.AdrenalinePain
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.api.body.limb.LimbCondition
import dev.krysztal.casualtiesbelow.api.event.LimbInjuryAllowCallback
import dev.krysztal.casualtiesbelow.api.event.LimbInjuryAppliedCallback
import dev.krysztal.casualtiesbelow.api.event.LimbInjuryAppliedContext
import dev.krysztal.casualtiesbelow.api.event.LimbInjuryContext
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.component.MutableLimbState

/** Central application point for limb injuries — the single internal path through which damage
  * attribution reaches a limb.
  *
  * Every application builds an immutable [[LimbInjuryContext]], asks
  * [[LimbInjuryAllowCallback.EVENT]] whether it may proceed, then commits pain and the caller's
  * stat mutation through [[BodyMutations]]. A committed result is reported through
  * [[LimbInjuryAppliedCallback.EVENT]]. Damage sources only supply their own rules: which parts are
  * hit, how severe, and which stats change.
  *
  * Sync: applied injuries mark the player dirty and are flushed to clients once at the end of the
  * server tick by [[BodyMutations]] — one packet per player per tick no matter how many injuries
  * the tick caused, and callers cannot forget to sync. Command-driven mutations sync immediately on
  * their own (instant feedback).
  */
object LimbInjuryService {

  /** Applies one injury to one limb. Returns `false` when a listener cancelled it.
    *
    * @param pain
    *   pain granted by this injury (from `PainCalc`), capped at the limb maximum on commit
    * @param jitter
    *   random fluctuation range applied to the injury's numbers: the damage is rolled once as
    *   `damage ± jitter` up front (listeners see the effective damage in the context), and the
    *   granted pain is rolled as `pain ± jitter` after permission callbacks run. Both clamp at 0; 0
    *   disables fluctuation
    * @param mutate
    *   the injury's own stat changes (muscle health, conditions, bleeding, ...) applied to a copy
    *   of the limb's current stats; receives the effective (jitter-rolled) damage
    */
  def apply(
      player: ServerPlayer,
      part: BodyPart,
      source: DamageSource,
      damage: Double,
      condition: Option[LimbCondition] = None,
      pain: Double = 0.0,
      jitter: Double = 3.0,
      ruleId: Option[Identifier] = None,
      profileId: Option[Identifier] = None,
      applicationType: Option[Identifier] = None,
      role: Option[String] = None
  )(mutate: (MutableLimbState, Double) => Unit): Boolean =
    applyInternal(
      player,
      part,
      source,
      damage,
      condition,
      pain,
      painMultiplier = 1.0,
      jitter,
      ruleId,
      profileId,
      applicationType,
      role
    )(mutate)

  /** Wound-system path that scales acute pain before permission callbacks. The multiplier affects
    * both the visible base pain and pain-only jitter; damage jitter and every non-pain mutation
    * remain unchanged.
    */
  private[casualtiesbelow] def applyWithPainMultiplier(
      player: ServerPlayer,
      part: BodyPart,
      source: DamageSource,
      damage: Double,
      condition: Option[LimbCondition],
      pain: Double,
      painMultiplier: Double,
      jitter: Double,
      ruleId: Option[Identifier],
      profileId: Option[Identifier],
      applicationType: Option[Identifier],
      role: Option[String]
  )(mutate: (MutableLimbState, Double) => Unit): Boolean =
    applyInternal(
      player,
      part,
      source,
      damage,
      condition,
      pain,
      painMultiplier,
      jitter,
      ruleId,
      profileId,
      applicationType,
      role
    )(mutate)

  private def applyInternal(
      player: ServerPlayer,
      part: BodyPart,
      source: DamageSource,
      damage: Double,
      condition: Option[LimbCondition],
      pain: Double,
      painMultiplier: Double,
      jitter: Double,
      ruleId: Option[Identifier],
      profileId: Option[Identifier],
      applicationType: Option[Identifier],
      role: Option[String]
  )(mutate: (MutableLimbState, Double) => Unit): Boolean = {
    val random = player.getRandom
    val normalizedPainMultiplier = AdrenalinePain.normalizeMultiplier(painMultiplier)

    val effectiveDamage = (damage + rollJitter(random, jitter)).max(0.0)
    val basePain = AdrenalinePain.scale(pain, normalizedPainMultiplier)
    val context = new LimbInjuryContext(
      player,
      part,
      source,
      effectiveDamage,
      condition.toJava,
      basePain,
      ruleId.toJava,
      profileId.toJava,
      applicationType.toJava,
      role.toJava
    )
    if (!LimbInjuryAllowCallback.EVENT.invoker().allowLimbInjury(context)) return false

    val grantedPain =
      (basePain + AdrenalinePain.scale(
        rollJitter(random, jitter),
        normalizedPainMultiplier
      )).max(0.0)

    val result = BodyMutations.mutate(player, part, markDirty = true) { stats =>
      stats.pain = (stats.pain + grantedPain).min(MutableLimbState.MaxValue)
      mutate(stats, effectiveDamage)
    }
    LimbInjuryAppliedCallback.EVENT
      .invoker()
      .onLimbInjuryApplied(
        new LimbInjuryAppliedContext(
          context,
          result.before,
          result.after,
          (result.after.pain - result.before.pain).max(0.0)
        )
      )
    true
  }

  /** Uniform roll in `[-jitter, +jitter]`. */
  private def rollJitter(random: RandomSource, jitter: Double): Double =
    (random.nextDouble() * 2.0 - 1.0) * jitter
}
