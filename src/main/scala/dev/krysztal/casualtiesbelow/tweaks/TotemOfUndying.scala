package dev.krysztal.casualtiesbelow.tweaks

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowDamageTypes
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.effect.CasualtiesBelowPotionEffects
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*
import dev.krysztal.casualtiesbelow.physiology.circulation.BloodVolume
import dev.krysztal.casualtiesbelow.physiology.circulation.HypoxiaProgression

/** Behavior grafted onto vanilla's totem of undying: vanilla has already consumed the item and
  * applied its own effects when [[onDeathProtection]] runs, and this object adds the physiological
  * side of the rescue.
  *
  * Every rescue grants a fixed skin/muscle recovery burst. Blood-loss and starvation rescues use
  * the bounded blood/hemostasis adapter: it restores a bounded blood reserve and temporarily
  * reduces the actual whole-body blood drain, while limb bleeding rates remain untouched so
  * clotting and treatment continue to operate on the real wounds. Terminal-hypoxia rescues reset
  * exposure and restore bounded oxygen/consciousness. Sepsis and forced vanilla deaths bypass the
  * vanilla method before an item can be consumed, so they never reach here. The hidden countdown is
  * server-authoritative and freezes whenever injury progression is frozen (creative or spectator
  * mode).
  */
private[casualtiesbelow] object TotemOfUndying {

  /** Completes the mod side of a successful vanilla death-protection rescue. */
  def onDeathProtection(player: ServerPlayer, killingDamage: DamageSource): Unit = {
    grantRecoveryEffects(player)
    if (
      killingDamage.is(CasualtiesBelowDamageTypes.BloodLoss) ||
      killingDamage.is(CasualtiesBelowDamageTypes.Starvation)
    ) {
      activate(player)
    } else if (killingDamage.is(CasualtiesBelowDamageTypes.Hypoxia)) {
      HypoxiaProgression.onDeathProtection(player)
    }
  }

  /** Grants a level-III skin/muscle recovery burst for 20 seconds after the rescue; the values are
    * fixed in place, mirroring vanilla's hardcoded totem grants.
    */
  def grantRecoveryEffects(player: ServerPlayer): Unit = {
    player.addEffect(new MobEffectInstance(CasualtiesBelowPotionEffects.SkinRegeneration, 400, 2))
    player.addEffect(new MobEffectInstance(CasualtiesBelowPotionEffects.MuscleRecovery, 400, 2))
  }

  /** Applies the fixed-balance rescue and immediately syncs the restored blood volume. */
  private def activate(player: ServerPlayer): Unit = {
    val vitals = player.vitals
    val effectiveMaxBlood = BloodVolume.effectiveMaximum(vitals)
    val restoreFraction =
      Consts.Bleeding.TotemBloodRestoreFraction
        .max(MinimumRestoreFraction)
        .min(1.0)
    val restoredBlood = effectiveMaxBlood * restoreFraction
    BloodVolume.restore(vitals, restoredBlood, effectiveMaxBlood)
    vitals.applyTotemHemostasisTicks(configuredDurationTicks)
  }

  /** Multiplier applied to this tick's summed external bleeding. At activation it is `1 - initial
    * reduction`, then approaches one linearly as the timer expires.
    */
  def bleedingMultiplier(vitals: VitalsComponentImpl): Double = {
    val duration = configuredDurationTicks
    val remaining = normalizeRemainingTicks(vitals.totemHemostasisTicks)
    if (duration <= 0 || remaining <= 0) return 1.0

    val initialReduction =
      Consts.Bleeding.TotemHemostasisInitialReduction
        .max(0.0)
        .min(1.0)
    1.0 - initialReduction * remaining.toDouble / duration.toDouble
  }

  /** Advances the hidden countdown without forcing a client sync. */
  def tick(vitals: VitalsComponentImpl): Unit = {
    val remaining = normalizeRemainingTicks(vitals.totemHemostasisTicks)
    vitals.applyTotemHemostasisTicks((remaining - 1).max(0))
  }

  def reset(vitals: VitalsComponentImpl): Unit = {
    vitals.applyTotemHemostasisTicks(0)
  }

  private[casualtiesbelow] def normalizeRemainingTicks(ticks: Int): Int = {
    ticks.max(0).min(configuredDurationTicks)
  }

  private def configuredDurationTicks: Int = {
    Consts.Bleeding.TotemHemostasisDurationTicks.max(0)
  }

  private val MinimumRestoreFraction = 0.000001
}
