package dev.krysztal.casualtiesbelow.mixin

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowDamageTypes
import dev.krysztal.casualtiesbelow.damage.FallDamageFormula
import dev.krysztal.casualtiesbelow.tweaks.TotemOfUndying

import com.llamalad7.mixinextras.injector.ModifyReturnValue
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** Replaces vanilla's linear fall damage calculation for players with a fixed power curve scaled by
  * the gameplay setting, and forwards successful vanilla death-protection rescues to the tweaks
  * layer.
  */
@Mixin(value = Array(classOf[LivingEntity]), remap = false)
abstract class LivingEntityMixin {
  @ModifyReturnValue(
    method = Array("calculateFallDamage"),
    at = Array(new At(value = "RETURN")),
    remap = false
  )
  private def calculateFallDamage(
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

  /** Vanilla has already consumed the death-protection item and applied its effects at RETURN; the
    * physiological side of the rescue lives in [[TotemOfUndying]].
    */
  @Inject(
    method = Array("checkTotemDeathProtection"),
    at = Array(new At(value = "RETURN")),
    remap = false
  )
  private def afterTotemDeathProtection(
      killingDamage: DamageSource,
      ci: CallbackInfoReturnable[Boolean]
  ): Unit = {
    if (!ci.getReturnValue) return

    this.asInstanceOf[LivingEntity] match {
      case player: ServerPlayer =>
        TotemOfUndying.onDeathProtection(player, killingDamage)
      case _ =>
    }
  }
}
