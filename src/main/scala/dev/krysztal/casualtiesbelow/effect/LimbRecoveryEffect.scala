package dev.krysztal.casualtiesbelow.effect

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.LivingEntity

import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.api.body.limb.LimbSnapshot
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.component.MutableLimbState
import dev.krysztal.casualtiesbelow.internal.extension.PlayerExtensions.body

/** Base class for the mod's standalone limb-recovery effects (Skin Regeneration, Muscle Recovery,
  * ...). Each tick the effect picks one limb whose [[healthOf]] is below maximum at random and
  * repairs it, mirroring vanilla Regeneration's cadence but on the mod's limb model.
  *
  * These effects own their gameplay logic: nothing applies or removes them on the player's behalf,
  * so they obey vanilla duration/amplifier semantics and work from any source (potions, commands,
  * beacons, other mods).
  */
private[casualtiesbelow] abstract class LimbRecoveryEffect(color: Int)
    extends MobEffect(MobEffectCategory.BENEFICIAL, color) {

  /** Health restored per tick per amplifier level. */
  protected def restorePerTick: Double

  /** Reads the health field this effect repairs from a limb snapshot. */
  protected def healthOf(stats: LimbSnapshot): Double

  /** Repairs the health field (and reconciles any coupled stats) on the mutable limb state. */
  protected def heal(state: MutableLimbState, restore: Double): Unit

  /** Discrete before→after transitions worth an immediate client sync; defaults to "reached full
    * health". Subclasses may OR additional transitions on top.
    */
  protected def isDiscreteTransition(before: LimbSnapshot, after: LimbSnapshot): Boolean = {
    healthOf(before) < LimbSnapshot.MaxValue && healthOf(after) >= LimbSnapshot.MaxValue
  }

  override def applyEffectTick(
      level: ServerLevel,
      entity: LivingEntity,
      amplifier: Int
  ): Boolean = {
    if (!entity.isInstanceOf[ServerPlayer]) return true
    val player = entity.asInstanceOf[ServerPlayer]

    val restore = restorePerTick * (amplifier + 1).toDouble

    val part = {
      val damaged = BodyPart.values
        .filter(part => healthOf(player.body.stats(part)) != LimbSnapshot.MaxValue)
      // NOTE: no damaged part
      if (!damaged.nonEmpty) None
      else Some(damaged(player.getRandom.nextInt(damaged.size)))
    }

    part.foreach { part =>
      // Throttled like Limb.tick: continuous regrowth rides the per-second body sync; only the
      // discrete transitions (a wound closing, a fracture healing) flush immediately.
      val mutation = BodyMutations.mutate(player, part, markDirty = false) { state =>
        heal(state, restore)
      }
      if (isDiscreteTransition(mutation.before, mutation.after)) {
        BodyMutations.markDirty(player)
      }
    }

    true
  }

  /** Applies every tick for smooth regrowth instead of vanilla Regeneration's interval pulses. */
  override def shouldApplyEffectTickThisTick(tickCount: Int, amplifier: Int): Boolean = true
}
