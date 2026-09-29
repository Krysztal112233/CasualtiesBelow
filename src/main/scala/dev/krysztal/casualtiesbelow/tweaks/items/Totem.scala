package dev.krysztal.casualtiesbelow.tweaks.items

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowDamageTypes
import dev.krysztal.casualtiesbelow.effect.CasualtiesBelowPotionEffects
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*
import dev.krysztal.casualtiesbelow.physiology.circulation.BloodVolume
import dev.krysztal.casualtiesbelow.physiology.circulation.HypoxiaProgression

/** Behavior grafted onto vanilla's totem of undying: vanilla has already consumed the item and
  * applied its own effects when [[onDeathProtection]] runs, and this object adds the physiological
  * side of the rescue.
  *
  * Every rescue grants a fixed skin/muscle recovery burst. Blood-loss and starvation rescues
  * additionally restore a bounded blood reserve; vanilla's own Regeneration grant keeps the blood
  * refilling afterwards. Terminal-hypoxia rescues reset exposure and restore bounded
  * oxygen/consciousness. Sepsis and forced vanilla deaths bypass the vanilla method before an item
  * can be consumed, so they never reach here.
  */
private[casualtiesbelow] object Totem {

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
    player.addEffect(new MobEffectInstance(CasualtiesBelowPotionEffects.BoneHealing, 400, 3))
    player.addEffect(new MobEffectInstance(CasualtiesBelowPotionEffects.MuscleRecovery, 400, 3))
    player.addEffect(new MobEffectInstance(CasualtiesBelowPotionEffects.SkinRegeneration, 400, 3))
    player.addEffect(new MobEffectInstance(CasualtiesBelowPotionEffects.SecondWind, 100, 2))
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
  }

  private val MinimumRestoreFraction = 0.000001
}
