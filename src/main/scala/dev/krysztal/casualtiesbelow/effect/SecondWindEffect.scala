package dev.krysztal.casualtiesbelow.effect

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.LivingEntity

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*
import dev.krysztal.casualtiesbelow.physiology.adrenaline.Adrenaline

/** Second Wind: steadily restores the adrenaline reserve each tick. */
private[casualtiesbelow] final class SecondWindEffect
    extends MobEffect(MobEffectCategory.BENEFICIAL, 0xf5d76e) {

  override def applyEffectTick(
      level: ServerLevel,
      entity: LivingEntity,
      amplifier: Int
  ): Boolean = {
    entity match {
      case player: ServerPlayer =>
        Adrenaline.grant(
          player,
          Consts.Regeneration.SecondWindAdrenalinePerTick * (amplifier + 1),
          CasualtiesBelowApi.id("second_wind")
        )
      case _ =>
    }
    true
  }

  override def shouldApplyEffectTickThisTick(tickCount: Int, amplifier: Int): Boolean = true
}
