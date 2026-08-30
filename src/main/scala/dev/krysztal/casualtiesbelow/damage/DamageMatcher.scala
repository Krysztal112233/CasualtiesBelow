package dev.krysztal.casualtiesbelow.damage

import scala.jdk.OptionConverters.*

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity

import dev.krysztal.casualtiesbelow.data.schema.WoundMatchData

/** Shared evaluator for datapack damage matchers. Wound and adrenaline rules deliberately share
  * this language while keeping independent priority chains and event effects.
  */
object DamageMatcher {

  def matches(
      damageMatch: WoundMatchData,
      level: ServerLevel,
      player: ServerPlayer,
      source: DamageSource
  ): Boolean = {
    val weapon = Option(source.getWeaponItem).filterNot(_.isEmpty)
    val directLiving =
      source.isDirect && Option(source.getDirectEntity).exists(_.isInstanceOf[LivingEntity])

    damageMatch.damageTypes.toScala.forall(_.matches(source)) &&
    damageMatch.excludedDamageTypes.toScala.forall(!_.matches(source)) &&
    damageMatch.victims.toScala.forall(_.contains(player.typeHolder())) &&
    damageMatch.predicate.toScala.forall(_.matches(level, player.position(), source)) &&
    damageMatch.directLiving.toScala.forall(_.booleanValue() == directLiving) &&
    damageMatch.armed.toScala.forall(_.booleanValue() == weapon.isDefined) &&
    damageMatch.weapon.toScala.forall(predicate => weapon.exists(predicate.test))
  }
}
