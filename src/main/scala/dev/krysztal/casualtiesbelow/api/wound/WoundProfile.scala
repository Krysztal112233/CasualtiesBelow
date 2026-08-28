package dev.krysztal.casualtiesbelow.api.wound

import java.util.concurrent.ConcurrentHashMap

import scala.jdk.OptionConverters.*

import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.api.data.CasualtiesBelowRegistries
import dev.krysztal.casualtiesbelow.api.data.GameplayDataLookup
import dev.krysztal.casualtiesbelow.api.data.WoundProfileData
import dev.krysztal.casualtiesbelow.api.data.WoundRuleData

/** How one damage kind wounds a limb: skin integrity and muscle health lost per half-heart of
  * damage, external bleeding rate granted per wound (zero = the wound never bleeds, e.g. burns
  * cauterize), and pain granted per half-heart.
  */
final case class WoundProfile(
    skinPerPoint: Double,
    musclePerPoint: Double,
    bleedRatePerWound: Double,
    painPerPoint: Double
)

/** A classified hit: its profile, whether the damage scatters as shrapnel across several random
  * body parts (explosions) instead of striking one located part, and whether the hit is forced onto
  * a specific part regardless of geometry (falling objects always land on the head).
  */
final case class Wound(
    profile: WoundProfile,
    scatter: Boolean,
    forcedPart: Option[BodyPart] = None
)

/** Evaluates datapack-defined wound rules against incoming damage sources. Rules are considered by
  * descending priority and ascending registry id; the first complete match with a resolvable wound
  * profile wins.
  */
object WoundProfiles {

  def classify(
      level: ServerLevel,
      player: ServerPlayer,
      source: DamageSource
  ): Option[Wound] = {
    GameplayDataLookup
      .orderedEntries(level.registryAccess(), CasualtiesBelowRegistries.WoundRule)(
        _.priority.intValue()
      )
      .iterator
      .filter { (_, rule) => matches(rule, level, player, source) }
      .flatMap { (ruleKey, rule) => resolve(ruleKey, rule, level) }
      .nextOption()
  }

  private def matches(
      rule: WoundRuleData,
      level: ServerLevel,
      player: ServerPlayer,
      source: DamageSource
  ): Boolean = {
    val weapon = Option(source.getWeaponItem).filterNot(_.isEmpty)
    val directLiving =
      source.isDirect && Option(source.getDirectEntity).exists(_.isInstanceOf[LivingEntity])

    rule.damageTypes.toScala.forall(_.contains(source.typeHolder())) &&
    rule.predicate.toScala.forall(_.matches(level, player.position(), source)) &&
    rule.directLiving.toScala.forall(_.booleanValue() == directLiving) &&
    rule.armed.toScala.forall(_.booleanValue() == weapon.isDefined) &&
    rule.weapon.toScala.forall(predicate => weapon.exists(predicate.test))
  }

  private def resolve(
      ruleKey: ResourceKey[WoundRuleData],
      rule: WoundRuleData,
      level: ServerLevel
  ): Option[Wound] = {
    GameplayDataLookup
      .entry(level.registryAccess(), CasualtiesBelowRegistries.WoundProfile, rule.profile)
      .map { profile =>
        Wound(
          WoundProfile(
            profile.skinPerPoint,
            profile.musclePerPoint,
            profile.bleedRatePerWound,
            profile.painPerPoint
          ),
          rule.scatter.booleanValue(),
          rule.forcedPart.toScala
        )
      }
      .orElse {
        if (WarnedMissingProfiles.add(rule.profile)) {
          CasualtiesBelow.Logger.warn(
            "Ignoring wound rule {} because profile {} is missing",
            ruleKey.identifier(),
            rule.profile.identifier()
          )
        }
        None
      }
  }

  private val WarnedMissingProfiles =
    ConcurrentHashMap.newKeySet[ResourceKey[WoundProfileData]]()
}
