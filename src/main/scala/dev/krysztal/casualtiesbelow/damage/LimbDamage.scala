package dev.krysztal.casualtiesbelow.damage

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents

import dev.krysztal.casualtiesbelow.api.wound.WoundProfiles
import dev.krysztal.casualtiesbelow.progression.StarvationProgression

/** Classifies incoming damage and delegates every matched rule to the central wound executor. */
object LimbDamage {

  def register(): Unit = {
    ServerLivingEntityEvents.AFTER_DAMAGE.register(afterDamage)
  }

  /** Invoked after a living entity takes damage, before armor/enchantment reduction, and only when
    * the entity survives. Server-side only (`LivingEntity.hurtServer`).
    */
  private val afterDamage: ServerLivingEntityEvents.AfterDamage =
    (entity, source, _, damageTaken, blocked) => onAfterDamage(entity, source, damageTaken, blocked)

  private def onAfterDamage(
      entity: LivingEntity,
      source: DamageSource,
      damageTaken: Float,
      blocked: Boolean
  ): Unit = {
    val player = entity match {
      case player: ServerPlayer => player
      case _                    => return
    }
    if (player.isCreative || player.isSpectator || blocked || damageTaken <= 0) return

    StarvationProgression.onAfterDamage(player, source, damageTaken)
    WoundProfiles
      .classify(player.level(), player, source)
      .foreach(rule => WoundApplications.execute(player, source, damageTaken.toDouble, rule))
  }
}
