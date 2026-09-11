package dev.krysztal.casualtiesbelow.mixin

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowDamageTypes
import dev.krysztal.casualtiesbelow.damage.FallDamageFormula
import dev.krysztal.casualtiesbelow.physiology.bleeding.TotemHemostasis
import dev.krysztal.casualtiesbelow.physiology.progression.HypoxiaProgression

import com.llamalad7.mixinextras.injector.ModifyReturnValue
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** Replaces vanilla's linear fall damage calculation with the configured power curve and adapts
  * successful vanilla death-protection returns to the mod's physiology state.
  */
@Mixin(value = Array(classOf[LivingEntity]), remap = false)
abstract class LivingEntityMixin {
  @ModifyReturnValue(
    method = Array("calculateFallDamage"),
    at = Array(new At(value = "RETURN")),
    remap = false
  )
  private def casualtiesbelow$calculateFallDamage(
      original: Int,
      fallDistance: Double,
      damageModifier: Float
  ): Int = {
    val entity = this.asInstanceOf[LivingEntity]
    if (FallDamageFormula.appliesTo(entity)) {
      FallDamageFormula.calculateCustom(entity, fallDistance, damageModifier)
    } else {
      original
    }
  }

  /** Vanilla has already consumed the death-protection item and applied its effects at RETURN.
    * Blood-loss and starvation rescues use the bounded blood/hemostasis adapter; terminal-hypoxia
    * rescues reset exposure and restore bounded oxygen/consciousness. Sepsis and forced vanilla
    * deaths bypass this method before an item can be consumed.
    */
  @Inject(
    method = Array("checkTotemDeathProtection"),
    at = Array(new At(value = "RETURN")),
    remap = false
  )
  private def casualtiesbelow$afterTotemDeathProtection(
      killingDamage: DamageSource,
      ci: CallbackInfoReturnable[Boolean]
  ): Unit = {
    if (!ci.getReturnValue) return

    this.asInstanceOf[LivingEntity] match {
      case player: ServerPlayer
          if killingDamage.is(CasualtiesBelowDamageTypes.BloodLoss) ||
            killingDamage.is(CasualtiesBelowDamageTypes.Starvation) =>
        TotemHemostasis.activate(player)
      case player: ServerPlayer if killingDamage.is(CasualtiesBelowDamageTypes.Hypoxia) =>
        HypoxiaProgression.onDeathProtection(player)
      case _ =>
    }
  }
}
