package dev.krysztal.casualtiesbelow.mixin

import net.minecraft.world.entity.LivingEntity

import dev.krysztal.casualtiesbelow.damage.FallDamageFormula

import com.llamalad7.mixinextras.injector.ModifyReturnValue
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

/** Replaces vanilla's linear fall damage calculation with the configured power curve. */
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
}
