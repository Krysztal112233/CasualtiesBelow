package dev.krysztal.casualtiesbelow.effect

import net.minecraft.core.Holder
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.effect.MobEffectInstance

/** Reconciles one player's derived vitals tier with its registered vanilla mob effect. */
private[effect] final class CasualtiesBelowCustomEffect(
    category: MobEffectCategory,
    color: Int,
    amplifierFromVitals: MobEffectAmplifierResolver = _ => None
) extends MobEffect(category, color)
    with VitalsEffectSynchronizer {

  private var effectHolder: Option[Holder[MobEffect]] = None

  private[effect] def bindEffectHolder(holder: Holder[MobEffect]): Unit = {
    effectHolder = Some(holder)
  }

  override def synchronizeFromVitals(player: ServerPlayer): Unit =
    effectHolder.foreach { holder =>
      val current = Option(player.getEffect(holder))

      amplifierFromVitals(player) match {
        // The active instance already matches the desired tier: nothing to reconcile.
        case Some(amplifier) if current.contains(desiredInstance(holder, amplifier)) => ()
        // Vanilla `addEffect` only applies strict upgrades (see MobEffectInstance#update) and
        // silently ignores downgrades, so the stale instance must go before the tier re-applies.
        case desired =>
          current.foreach(_ => player.removeEffect(holder))
          desired.foreach(amplifier => player.addEffect(desiredInstance(holder, amplifier)))
      }
    }

  /** The canonical shape this effect reconciles towards: infinite duration, non-ambient, hidden
    * particles, shown icon.
    */
  private def desiredInstance(holder: Holder[MobEffect], amplifier: Int): MobEffectInstance =
    new MobEffectInstance(
      holder,
      MobEffectInstance.INFINITE_DURATION,
      amplifier,
      ambient = false,
      visible = false,
      showIcon = true
    )
}
