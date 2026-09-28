package dev.krysztal.casualtiesbelow.mixin

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity

import dev.krysztal.casualtiesbelow.tweaks.effects.RegenerationEffect

import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** Redirects vanilla Regeneration's heart-healing pulses to the tweaks layer for players; the mod
  * bypasses the vanilla health bar entirely, so the pulse is re-expressed as blood-volume recovery.
  * The vanilla class is package-private, so the mixin targets it by name. The pulse cadence stays
  * vanilla (`shouldApplyEffectTickThisTick` is untouched); non-player mobs keep vanilla heart
  * healing.
  */
@Mixin(targets = Array("net.minecraft.world.effect.RegenerationMobEffect"), remap = false)
abstract class RegenerationMobEffectMixin {

  @Inject(
    method = Array("applyEffectTick"),
    at = Array(new At(value = "HEAD")),
    cancellable = true,
    remap = false
  )
  private def redirectPulseToBloodVolume(
      level: ServerLevel,
      mob: LivingEntity,
      amplification: Int,
      cir: CallbackInfoReturnable[Boolean]
  ): Unit = {
    mob match {
      case player: ServerPlayer =>
        RegenerationEffect.onPulse(player, amplification)
        cir.setReturnValue(true)
      case _ =>
    }
  }
}
