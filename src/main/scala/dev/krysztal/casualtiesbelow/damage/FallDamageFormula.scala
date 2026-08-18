package dev.krysztal.casualtiesbelow.damage

import net.minecraft.tags.EntityTypeTags
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player

import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

/** Configurable power curve for vanilla fall damage. */
object FallDamageFormula {
  private val VanillaDistanceEpsilon = 1.0e-6

  def appliesTo(entity: LivingEntity): Boolean =
    CasualtiesBelowConfig.AffectAllLivingEntities.get() || entity.isInstanceOf[Player]

  def calculateCustom(
      entity: LivingEntity,
      fallDistance: Double,
      damageModifier: Float
  ): Int = {
    if (entity.is(EntityTypeTags.FALL_DAMAGE_IMMUNE)) {
      0
    } else {
      val distanceBeyondSafe =
        fallDistance + VanillaDistanceEpsilon - entity.getAttributeValue(
          Attributes.SAFE_FALL_DISTANCE
        )
      val baseDamage = math.pow(
        math.max(0.0, distanceBeyondSafe),
        CasualtiesBelowConfig.FallDamageExponent.get().doubleValue()
      ) * CasualtiesBelowConfig.FallDamageScale.get().doubleValue()
      val damage =
        baseDamage *
          damageModifier *
          entity.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER)
      Mth.floor(damage)
    }
  }
}
