package dev.krysztal.casualtiesbelow.api.wound

import java.util.concurrent.ConcurrentHashMap

import scala.jdk.OptionConverters.*

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.api.data.GameplayCodecs
import dev.krysztal.casualtiesbelow.api.data.GameplayDataLookup
import dev.krysztal.casualtiesbelow.api.data.GameplayDataStore
import dev.krysztal.casualtiesbelow.api.data.GameplayDataStores
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

object WoundProfile {
  val Codec: Codec[WoundProfile] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        GameplayCodecs.NonNegativeDouble.fieldOf("skin_per_point").forGetter(_.skinPerPoint),
        GameplayCodecs.NonNegativeDouble.fieldOf("muscle_per_point").forGetter(_.musclePerPoint),
        GameplayCodecs.NonNegativeDouble
          .fieldOf("bleed_rate_per_wound")
          .forGetter(_.bleedRatePerWound),
        GameplayCodecs.NonNegativeDouble.fieldOf("pain_per_point").forGetter(_.painPerPoint)
      )
      .apply(instance, WoundProfile.apply)
  )
}

/** A classified hit: its profile, whether the damage scatters as shrapnel across several random
  * body parts (explosions), whether it is forced onto a specific part regardless of geometry
  * (falling objects always land on the head), and optional weights for positionless hits.
  */
final case class Wound(
    profile: WoundProfile,
    scatter: Boolean,
    forcedPart: Option[BodyPart] = None,
    weights: Option[Map[BodyPart, Double]] = None
)

/** Evaluates datapack-defined wound rules against incoming damage sources. Rules are considered by
  * descending priority and ascending datapack id; the first complete match with a resolvable wound
  * profile wins.
  */
object WoundProfiles {

  def classify(
      level: ServerLevel,
      player: ServerPlayer,
      source: DamageSource
  ): Option[Wound] = {
    val store = GameplayDataStores.server
    GameplayDataLookup
      .orderedEntries(store.woundRules)(_.priority.intValue())
      .iterator
      .filter { (_, rule) => matches(rule, level, player, source) }
      .flatMap { (ruleId, rule) => resolve(ruleId, rule, store) }
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
      ruleId: Identifier,
      rule: WoundRuleData,
      store: GameplayDataStore
  ): Option[Wound] = {
    GameplayDataLookup
      .entry(store.woundProfiles, rule.profile)
      .map(profile =>
        Wound(
          profile,
          rule.scatter.booleanValue(),
          rule.forcedPart.toScala,
          rule.weights.toScala
        )
      )
      .orElse {
        if (WarnedMissingProfiles.add(rule.profile)) {
          CasualtiesBelow.Logger.warn(
            "Ignoring wound rule {} because profile {} is missing",
            ruleId,
            rule.profile
          )
        }
        None
      }
  }

  private val WarnedMissingProfiles = ConcurrentHashMap.newKeySet[Identifier]()

  /** Clears the missing-profile warning dedup on datapack reload, so a reference broken again by a
    * later reload warns again instead of staying silently ignored.
    */
  private[casualtiesbelow] def clearWarnedMissingProfiles(): Unit = WarnedMissingProfiles.clear()
}
