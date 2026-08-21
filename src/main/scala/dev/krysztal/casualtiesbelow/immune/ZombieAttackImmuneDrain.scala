package dev.krysztal.casualtiesbelow.immune

import net.minecraft.tags.EntityTypeTags
import net.minecraft.world.entity.player.Player

import dev.krysztal.casualtiesbelow.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.event.LimbInjuryCallback
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

/** Zombie-family hits drain immune health, once per hit with no cooldown: getting mobbed is
  * supposed to be deadly for the immune system, and avoiding it is the player's job. One-way
  * feedback by design: only external attacks drain immune health — infections, bleeding and pain
  * never do, so the infection loop cannot spiral.
  *
  * Implemented as a [[LimbInjuryCallback]] listener: the drain happens alongside the injury, and
  * this listener never cancels it.
  */
object ZombieAttackImmuneDrain {

  def register(): Unit =
    LimbInjuryCallback.EVENT.register { context =>
      val isZombieHit =
        Option(context.source.getEntity).exists(_.is(EntityTypeTags.ZOMBIES))
      if (isZombieHit) {
        drain(context.player)
      }
      true
    }

  /** Drains immune health by the configured amount rolled with proportional jitter (`drain × (1 ±
    * jitter)`), floored at zero.
    */
  private def drain(player: Player): Unit = {
    val vitals = CasualtiesBelowComponents.Vitals.get(player)
    val base = CasualtiesBelowConfig.ZombieHitImmuneDrain.get()
    val jitter = CasualtiesBelowConfig.ZombieHitImmuneDrainJitter.get()
    val roll = 1.0 + (player.getRandom.nextFloat() * 2.0 - 1.0) * jitter
    val next = (vitals.immuneHealth - base * roll).max(0.0)
    if (next == vitals.immuneHealth) return

    vitals.immuneHealth = next
    CasualtiesBelowComponents.Vitals.sync(player)
  }
}
