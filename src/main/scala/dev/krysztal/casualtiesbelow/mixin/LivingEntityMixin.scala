package dev.krysztal.casualtiesbelow.mixin

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowDamageTypes
import dev.krysztal.casualtiesbelow.bleeding.TotemHemostasis
import dev.krysztal.casualtiesbelow.damage.FallDamageFormula
import dev.krysztal.casualtiesbelow.damage.LimbDamage

import com.llamalad7.mixinextras.injector.ModifyReturnValue
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** Replaces vanilla's linear fall damage calculation with the configured power curve, and routes
  * fall impacts into limb injury attribution.
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

  /** `calculateFallDamage` stays side-effect free (mob AI calls it for fall prediction); the actual
    * impact is observed here, at the only vanilla call site that applies the damage.
    */
  @Inject(
    method = Array("causeFallDamage"),
    at = Array(new At(value = "RETURN")),
    remap = false
  )
  private def casualtiesbelow$causeFallDamage(
      fallDistance: Double,
      damageModifier: Float,
      damageSource: DamageSource,
      ci: CallbackInfoReturnable[Boolean]
  ): Unit = {
    LimbDamage.onFallDamage(
      this.asInstanceOf[LivingEntity],
      fallDistance,
      damageModifier,
      damageSource,
      ci.getReturnValue
    )
  }

  /** Vanilla has already consumed the death-protection item and applied its effects at RETURN. Only
    * a successful blood-loss rescue receives the physiological blood/hemostasis adapter; sepsis and
    * forced vanilla deaths bypass this method before an item can be consumed.
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
    if (ci.getReturnValue && killingDamage.is(CasualtiesBelowDamageTypes.BloodLoss)) {
      this.asInstanceOf[LivingEntity] match {
        case player: ServerPlayer => TotemHemostasis.activate(player)
        case _                    =>
      }
    }
  }
}
