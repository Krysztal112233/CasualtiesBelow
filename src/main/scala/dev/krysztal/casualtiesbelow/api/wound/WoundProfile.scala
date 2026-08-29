package dev.krysztal.casualtiesbelow.api.wound

import scala.jdk.OptionConverters.*

import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.api.data.FallRulesData
import dev.krysztal.casualtiesbelow.api.data.GameplayCodecs
import dev.krysztal.casualtiesbelow.api.data.GameplayDataLookup
import dev.krysztal.casualtiesbelow.api.data.GameplayDataStore
import dev.krysztal.casualtiesbelow.api.data.GameplayDataStores
import dev.krysztal.casualtiesbelow.api.data.LegacyWoundRuleData
import dev.krysztal.casualtiesbelow.api.data.WoundMatchData
import dev.krysztal.casualtiesbelow.api.data.WoundRuleData
import dev.krysztal.casualtiesbelow.api.data.WoundRuleV2Data

/** How one damage kind wounds a limb: skin integrity and muscle health lost per half-heart of
  * damage, external bleeding rate granted per wound (zero = the wound never bleeds, e.g. burns
  * cauterize), and pain granted per half-heart.
  */
final case class WoundProfile(
    skinPerPoint: Double,
    musclePerPoint: Double,
    bleedRatePerWound: Double,
    painPerPoint: Double,
    profileType: Identifier = WoundProfile.LinearType
)

object WoundProfile {
  val LinearType: Identifier = CasualtiesBelow.ofIdentifier("linear")

  private val RawCodec: Codec[WoundProfile] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        GameplayCodecs
          .defaultedField(Identifier.CODEC, "type", LinearType)
          .forGetter(_.profileType),
        GameplayCodecs.NonNegativeDouble.fieldOf("skin_per_point").forGetter(_.skinPerPoint),
        GameplayCodecs.NonNegativeDouble.fieldOf("muscle_per_point").forGetter(_.musclePerPoint),
        GameplayCodecs.NonNegativeDouble
          .fieldOf("bleed_rate_per_wound")
          .forGetter(_.bleedRatePerWound),
        GameplayCodecs.NonNegativeDouble.fieldOf("pain_per_point").forGetter(_.painPerPoint)
      )
      .apply(
        instance,
        (profileType, skin, muscle, bleed, pain) =>
          WoundProfile(skin, muscle, bleed, pain, profileType)
      )
  )

  /** Profile response types are namespaced so further models can be added without another schema
    * change. V2 ships the behavior-preserving linear response first.
    */
  val Codec: Codec[WoundProfile] = RawCodec.validate(profile =>
    if (profile.profileType == LinearType) DataResult.success(profile)
    else DataResult.error(() => s"Unknown wound profile type: ${profile.profileType}")
  )
}

final case class ResolvedWoundContribution(
    profileId: Identifier,
    profile: WoundProfile,
    severityMultiplier: Double
)

sealed trait ResolvedWoundApplication

final case class ResolvedTypedWoundApplication(
    data: WoundApplicationData,
    wounds: List[ResolvedWoundContribution],
    legacyFallRules: List[FallRulesData]
) extends ResolvedWoundApplication

/** Compatibility adapter for the original flat wound-rule schema. Its exact-fall behavior is
  * isolated here; native V2 applications never inspect a hard-coded damage type.
  */
final case class ResolvedLegacyWoundApplication(
    profileId: Identifier,
    profile: WoundProfile,
    scatter: Boolean,
    forcedPart: Option[BodyPart] = None,
    weights: Option[Map[BodyPart, Double]] = None,
    fallRules: List[FallRulesData] = List.empty
) extends ResolvedWoundApplication

final case class ClassifiedWoundRule(
    ruleId: Identifier,
    applications: List[ResolvedWoundApplication]
)

/** Evaluates datapack-defined wound rules against incoming damage sources. Rules are considered by
  * descending priority and ascending datapack id; the first complete match with a resolvable wound
  * profile wins.
  */
object WoundProfiles {

  private final case class CompiledRule(
      id: Identifier,
      rule: WoundRuleData,
      applications: List[ResolvedWoundApplication]
  )

  @volatile private var compiled: List[CompiledRule] = List.empty
  @volatile private var initialized = false

  def classify(
      level: ServerLevel,
      player: ServerPlayer,
      source: DamageSource
  ): Option[ClassifiedWoundRule] = {
    ensureCompiled()
    compiled
      .find(entry => matches(entry.rule, level, player, source))
      .map(entry => ClassifiedWoundRule(entry.id, entry.applications))
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

    rule match {
      case current: WoundRuleV2Data =>
        matchesV2(current.woundMatch, level, player, source, directLiving, weapon)
      case legacy: LegacyWoundRuleData =>
        legacy.damageTypes.toScala.forall(_.contains(source.typeHolder())) &&
        legacy.predicate.toScala.forall(_.matches(level, player.position(), source)) &&
        legacy.directLiving.toScala.forall(_.booleanValue() == directLiving) &&
        legacy.armed.toScala.forall(_.booleanValue() == weapon.isDefined) &&
        legacy.weapon.toScala.forall(predicate => weapon.exists(predicate.test))
    }
  }

  private def matchesV2(
      woundMatch: WoundMatchData,
      level: ServerLevel,
      player: ServerPlayer,
      source: DamageSource,
      directLiving: Boolean,
      weapon: Option[net.minecraft.world.item.ItemStack]
  ): Boolean = {
    woundMatch.damageTypes.toScala.forall(_.matches(source)) &&
    woundMatch.excludedDamageTypes.toScala.forall(!_.matches(source)) &&
    woundMatch.victims.toScala.forall(_.contains(player.typeHolder())) &&
    woundMatch.predicate.toScala.forall(_.matches(level, player.position(), source)) &&
    woundMatch.directLiving.toScala.forall(_.booleanValue() == directLiving) &&
    woundMatch.armed.toScala.forall(_.booleanValue() == weapon.isDefined) &&
    woundMatch.weapon.toScala.forall(predicate => weapon.exists(predicate.test))
  }

  /** Rebuilds every rule against one immutable gameplay-data view, then publishes the complete
    * ordered result with one volatile write. Hits during reload continue to use the previous view.
    */
  private[casualtiesbelow] def rebuild(): Unit = synchronized {
    val store = GameplayDataStores.server
    val fallRules =
      GameplayDataLookup.orderedEntries(store.fallRules)(_.priority.intValue()).map(_._2)
    compiled = GameplayDataLookup
      .orderedEntries(store.woundRules)(_.priority.intValue())
      .flatMap { (ruleId, rule) => compileRule(ruleId, rule, store, fallRules) }
    initialized = true
    CasualtiesBelow.Logger.info("Compiled {} wound rules", Int.box(compiled.size))
  }

  private[casualtiesbelow] def clear(): Unit = synchronized {
    compiled = List.empty
    initialized = false
  }

  private def ensureCompiled(): Unit = {
    if (!initialized) rebuild()
  }

  private def compileRule(
      ruleId: Identifier,
      rule: WoundRuleData,
      store: GameplayDataStore,
      fallRules: List[FallRulesData]
  ): Option[CompiledRule] = {
    rule match {
      case current: WoundRuleV2Data =>
        val resolved = current.applications.map(application =>
          resolveApplication(ruleId, application, store, fallRules)
        )
        if (resolved.forall(_.isDefined)) {
          Some(CompiledRule(ruleId, rule, resolved.flatten))
        } else None
      case legacy: LegacyWoundRuleData =>
        resolveProfile(ruleId, legacy.profile, store).map { profile =>
          CompiledRule(
            ruleId,
            rule,
            List(
              ResolvedLegacyWoundApplication(
                legacy.profile,
                profile,
                legacy.scatter.booleanValue(),
                legacy.forcedPart.toScala,
                legacy.weights.toScala,
                fallRules
              )
            )
          )
        }
    }
  }

  private def resolveApplication(
      ruleId: Identifier,
      application: WoundApplicationData,
      store: GameplayDataStore,
      fallRules: List[FallRulesData]
  ): Option[ResolvedWoundApplication] = {
    val resolved = application.wounds.map { wound =>
      resolveProfile(ruleId, wound.profile, store).map(profile =>
        ResolvedWoundContribution(wound.profile, profile, wound.severityMultiplier)
      )
    }
    if (resolved.forall(_.isDefined)) {
      val legacy = application match {
        case paired: PairedImpactApplicationData if paired.legacyFallRules.booleanValue() =>
          fallRules
        case _ => List.empty
      }
      Some(ResolvedTypedWoundApplication(application, resolved.flatten, legacy))
    } else None
  }

  private def resolveProfile(
      ruleId: Identifier,
      profileId: Identifier,
      store: GameplayDataStore
  ): Option[WoundProfile] = {
    GameplayDataLookup.entry(store.woundProfiles, profileId).orElse {
      CasualtiesBelow.Logger.warn(
        "Ignoring wound rule {} because profile {} is missing",
        ruleId,
        profileId
      )
      None
    }
  }

}
