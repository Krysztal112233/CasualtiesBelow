package dev.krysztal.casualtiesbelow.damage

import net.minecraft.tags.EntityTypeTags
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player

import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.Consts

/** Player-only fall damage formula, compiled from the fixed balance expression via EvalEx. */
object FallDamageFormula {
  private val VanillaDistanceEpsilon = 1.0e-6

  private val formula = Consts.Fall.FallDamageFormula

  def appliesTo(entity: LivingEntity): Boolean = entity.isInstanceOf[Player]

  def calculateCustom(
      entity: LivingEntity,
      fallDistance: Double,
      damageModifier: Float
  ): Int = {
    if (entity.is(EntityTypeTags.FALL_DAMAGE_IMMUNE)) {
      0
    } else {
      Mth.floor(
        formula.evaluate(
          fallDistance + VanillaDistanceEpsilon,
          entity.getAttributeValue(Attributes.SAFE_FALL_DISTANCE),
          damageModifier.toDouble,
          entity.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER)
        ) * CasualtiesBelowConfig.injurySurvival.fallDamageMultiplier.get()
      )
    }
  }
}
