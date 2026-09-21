package dev.krysztal.casualtiesbelow.physiology.immune

import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.EntityTypeTags

import dev.krysztal.casualtiesbelow.api.event.TraumaStartedCallback
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.extension.ConfigValueExtensions.*
import dev.krysztal.casualtiesbelow.internal.extension.PlayerExtensions.*

/** Zombie-family hits drain immune health, once per hit with no cooldown: getting mobbed is
  * supposed to be deadly for the immune system, and avoiding it is the player's job. One-way
  * feedback by design: only external attacks drain immune health — infections, bleeding and pain
  * never do, so the infection loop cannot spiral.
  *
  * Implemented as a [[TraumaStartedCallback]] listener so scatter, paired impacts, conditions, and
  * multiple profiles from one hit cannot multiply the drain.
  */
object ZombieAttackImmuneDrain {

  def register(): Unit =
    TraumaStartedCallback.EVENT.register { context =>
      val isZombieHit =
        Option(context.source.getEntity).exists(_.is(EntityTypeTags.ZOMBIES))
      if (context.woundsAllowed && isZombieHit) {
        drain(context.player)
      }
    }

  /** Drains immune health by the configured amount rolled with proportional jitter (`drain × (1 ±
    * jitter)`), floored at zero.
    */
  private def drain(player: ServerPlayer): Unit = {
    val vitals = player.vitals
    val base = CasualtiesBelowConfig.ZombieHitImmuneDrain.value
    val jitter = CasualtiesBelowConfig.ZombieHitImmuneDrainJitter.value
    val roll = 1.0 + (player.getRandom.nextFloat() * 2.0 - 1.0) * jitter
    val next = (vitals.infection.immuneHealth - base * roll).max(0.0)
    if (next == vitals.infection.immuneHealth) return

    VitalsMutations.setImmuneHealth(vitals, next)
    VitalsMutations.syncNow(player)
  }
}
