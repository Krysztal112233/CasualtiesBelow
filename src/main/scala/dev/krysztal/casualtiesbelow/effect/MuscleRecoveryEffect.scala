package dev.krysztal.casualtiesbelow.effect

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.LivingEntity

import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.api.body.limb.LimbSnapshot
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extension.PlayerExtensions.body

/** Muscle Recovery: a genuinely independent beneficial effect that each tick picks one
  * muscle-damaged limb at random and repairs it. Mirrors [[SkinRegenerationEffect]], minus the
  * bleeding-cap reconciliation — muscle regrowth has no coupled stat (see Limb.tickMuscleRegen).
  *
  * Like Skin Regeneration, this effect owns its gameplay logic: nothing applies or removes it on
  * the player's behalf, so it obeys vanilla duration/amplifier semantics and works from any source
  * (potions, commands, beacons, other mods).
  */
private[casualtiesbelow] final class MuscleRecoveryEffect
    extends MobEffect(MobEffectCategory.BENEFICIAL, 0xc0392b) {

  override def applyEffectTick(
      level: ServerLevel,
      entity: LivingEntity,
      amplifier: Int
  ): Boolean = {
    if (!entity.isInstanceOf[ServerPlayer]) return true
    val player = entity.asInstanceOf[ServerPlayer]

    val restore =
      Consts.Regeneration.MuscleRecoveryEffectPerTick * (amplifier + 1).toDouble

    val part = {
      val damaged = BodyPart.values
        .filter(player.body.stats(_).muscleHealth != LimbSnapshot.MaxValue)
      // NOTE: no damaged part
      if (!damaged.nonEmpty) None
      else Some(damaged(player.getRandom.nextInt(damaged.size)))
    }

    part.foreach { part =>
      val mutation = BodyMutations.mutate(player, part) { state =>
        if (state.muscleHealth < LimbSnapshot.MaxValue) {
          state.muscleHealth = (state.muscleHealth + restore).min(LimbSnapshot.MaxValue)
        }
      }
      val reachedFullMuscle =
        mutation.before.muscleHealth < LimbSnapshot.MaxValue &&
          mutation.after.muscleHealth >= LimbSnapshot.MaxValue
      if (reachedFullMuscle) BodyMutations.markDirty(player)
    }

    true
  }

  /** Applies every tick for smooth regrowth instead of vanilla Regeneration's interval pulses. */
  override def shouldApplyEffectTickThisTick(tickCount: Int, amplifier: Int): Boolean = true

}
