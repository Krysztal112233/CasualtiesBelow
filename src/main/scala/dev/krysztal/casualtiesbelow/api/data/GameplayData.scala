package dev.krysztal.casualtiesbelow.api.data

import java.lang.Boolean as JBoolean
import java.lang.Double as JDouble
import java.util.Optional

import scala.jdk.CollectionConverters.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.mojang.serialization.Codec
import com.mojang.serialization.Codec.BOOL
import com.mojang.serialization.Codec.INT
import com.mojang.serialization.Codec.unboundedMap
import com.mojang.serialization.Codec.withAlternative
import com.mojang.serialization.DataResult
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.advancements.predicates.DamageSourcePredicate
import net.minecraft.advancements.predicates.ItemPredicate
import net.minecraft.core.HolderSet
import net.minecraft.core.RegistryCodecs
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.util.ExtraCodecs
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.config.FormulaConfigValue

import com.ezylang.evalex.Expression

/** Shared codec validation for datapack-defined gameplay values. */
private[data] object GameplayCodecs {
  val NonNegativeDouble: Codec[Double] = Codec.DOUBLE.comapFlatMap(
    value =>
      if (JDouble.isFinite(value) && value >= 0.0) DataResult.success(value)
      else DataResult.error(() => s"Expected a finite, non-negative number, got $value"),
    identity
  )

  val UnitDouble: Codec[Double] = Codec.DOUBLE.comapFlatMap(
    value =>
      if (JDouble.isFinite(value) && value >= 0.0 && value <= 1.0)
        DataResult.success(value)
      else DataResult.error(() => s"Expected a finite number in [0, 1], got $value"),
    identity
  )
}

/** An EvalEx formula source loaded from a datapack. Compilation is lazy for normal use; the codec
  * validates each source eagerly with dummy values so malformed entries fail during reload.
  */
final class FormulaSource private (val source: String, val variables: Seq[String]) {
  lazy val expression: Expression = FormulaConfigValue.compile(source, variables)

  /** Evaluates this source with values in declaration order. Runtime failures yield `None` so the
    * consumer can retain its config-formula fallback. Expressions are mutable and evaluation must
    * stay on the server thread.
    */
  def evaluate(values: Double*): Option[Double] = {
    if (values.length != variables.length) {
      CasualtiesBelow.Logger.warn(
        "Formula '{}' expected {} values ({}), got {}",
        source,
        variables.length,
        variables.mkString(", "),
        values.length
      )
      return None
    }

    Try {
      variables.lazyZip(values).foreach { (name, value) =>
        expression.`with`(name, value)
      }
      expression.evaluate().getNumberValue().doubleValue()
    } match {
      case Success(value) => Some(value)
      case Failure(error) =>
        CasualtiesBelow.Logger.warn(
          "Gameplay data formula '{}' evaluation failed: {}",
          source,
          error.getMessage
        )
        None
    }
  }

  override def equals(other: Any): Boolean = other match {
    case that: FormulaSource => source == that.source && variables == that.variables
    case _                   => false
  }

  override def hashCode(): Int = (source, variables).hashCode()
  override def toString: String = source
}

object FormulaSource {
  def apply(source: String, variables: Seq[String]): FormulaSource =
    new FormulaSource(source, variables)

  def codec(variables: Seq[String]): Codec[FormulaSource] = Codec.STRING.comapFlatMap(
    source =>
      Try {
        val expression = FormulaConfigValue.compile(source, variables)
        expression.evaluate()
        FormulaSource(source, variables)
      } match {
        case Success(formula) => DataResult.success(formula)
        case Failure(error)   =>
          DataResult.error(() => s"Invalid formula '$source': ${error.getMessage}")
      },
    _.source
  )
}

/** Datapack-defined coefficients for one wound kind. */
final case class WoundProfileData(
    skinPerPoint: Double,
    musclePerPoint: Double,
    bleedRatePerWound: Double,
    painPerPoint: Double
)

object WoundProfileData {
  val Codec: Codec[WoundProfileData] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        GameplayCodecs.NonNegativeDouble.fieldOf("skin_per_point").forGetter(_.skinPerPoint),
        GameplayCodecs.NonNegativeDouble.fieldOf("muscle_per_point").forGetter(_.musclePerPoint),
        GameplayCodecs.NonNegativeDouble
          .fieldOf("bleed_rate_per_wound")
          .forGetter(_.bleedRatePerWound),
        GameplayCodecs.NonNegativeDouble.fieldOf("pain_per_point").forGetter(_.painPerPoint)
      )
      .apply(instance, WoundProfileData.apply)
  )
}

/** One ordered damage-source classification rule. Optional matchers are ANDed by consumers. */
final case class WoundRuleData(
    damageTypes: Optional[HolderSet[DamageType]],
    predicate: Optional[DamageSourcePredicate],
    directLiving: Optional[JBoolean],
    armed: Optional[JBoolean],
    weapon: Optional[ItemPredicate],
    profile: Identifier,
    scatter: JBoolean,
    forcedPart: Optional[BodyPart],
    priority: Integer
)

object WoundRuleData {
  val Codec: Codec[WoundRuleData] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        RegistryCodecs
          .homogeneousList(Registries.DAMAGE_TYPE)
          .optionalFieldOf("damage_types")
          .forGetter(_.damageTypes),
        DamageSourcePredicate.CODEC.optionalFieldOf("predicate").forGetter(_.predicate),
        BOOL
          .optionalFieldOf("direct_living")
          .forGetter(_.directLiving),
        BOOL.optionalFieldOf("armed").forGetter(_.armed),
        ItemPredicate.CODEC.optionalFieldOf("weapon").forGetter(_.weapon),
        Identifier.CODEC.fieldOf("profile").forGetter(_.profile),
        BOOL.optionalFieldOf("scatter", false).forGetter(_.scatter),
        BodyPart.Codec.optionalFieldOf("forced_part").forGetter(_.forcedPart),
        INT.optionalFieldOf("priority", 0).forGetter(_.priority)
      )
      .apply(instance, WoundRuleData.apply)
  )
}

/** Per-item armor formula overrides. */
final case class ArmorProtectionData(
    items: HolderSet[Item],
    skinFactor: Optional[FormulaSource],
    muscleFactor: Optional[FormulaSource],
    priority: Integer
)

object ArmorProtectionData {
  private val RawCodec: Codec[ArmorProtectionData] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        RegistryCodecs.homogeneousList(Registries.ITEM).fieldOf("items").forGetter(_.items),
        FormulaSource
          .codec(Seq("armor", "toughness"))
          .optionalFieldOf("skin_factor")
          .forGetter(_.skinFactor),
        FormulaSource
          .codec(Seq("armor", "toughness", "skinFactor"))
          .optionalFieldOf("muscle_factor")
          .forGetter(_.muscleFactor),
        INT.optionalFieldOf("priority", 0).forGetter(_.priority)
      )
      .apply(instance, ArmorProtectionData.apply)
  )

  val Codec: Codec[ArmorProtectionData] = RawCodec.validate(data =>
    if (data.skinFactor.isPresent || data.muscleFactor.isPresent) DataResult.success(data)
    else DataResult.error(() => "At least one of skin_factor or muscle_factor is required")
  )
}

/** Per-item discomfort override. Exactly one of level or mean is present. */
final case class DiscomfortData(
    items: HolderSet[Item],
    level: Optional[Integer],
    mean: Optional[Double],
    priority: Integer
)

object DiscomfortData {
  private val RawCodec: Codec[DiscomfortData] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        RegistryCodecs.homogeneousList(Registries.ITEM).fieldOf("items").forGetter(_.items),
        ExtraCodecs.intRange(1, 3).optionalFieldOf("level").forGetter(_.level),
        GameplayCodecs.NonNegativeDouble.optionalFieldOf("mean").forGetter(_.mean),
        INT.optionalFieldOf("priority", 0).forGetter(_.priority)
      )
      .apply(instance, DiscomfortData.apply)
  )

  val Codec: Codec[DiscomfortData] = RawCodec.validate(data =>
    if (data.level.isPresent != data.mean.isPresent) DataResult.success(data)
    else DataResult.error(() => "Exactly one of level or mean is required")
  )
}

/** Per-entity fall impact and condition rules. */
final case class FallRulesData(
    entities: HolderSet[EntityType[?]],
    muscleDamagePerPoint: Double = FallRulesData.DefaultMuscleDamagePerPoint,
    scrapeThreshold: Double = FallRulesData.DefaultScrapeThreshold,
    scrapePerPoint: Double = FallRulesData.DefaultScrapePerPoint,
    dislocationThreshold: Double = FallRulesData.DefaultDislocationThreshold,
    fractureThreshold: Double = FallRulesData.DefaultFractureThreshold,
    fractureBaseRecoveryTicks: Integer = FallRulesData.DefaultFractureBaseRecoveryTicks,
    fallBleedingRatePerWound: Double = FallRulesData.DefaultFallBleedingRatePerWound,
    fallPainPerPoint: Double = FallRulesData.DefaultFallPainPerPoint,
    fracturePain: Double = FallRulesData.DefaultFracturePain,
    dislocationPain: Double = FallRulesData.DefaultDislocationPain,
    priority: Integer = 0
)

object FallRulesData {
  val DefaultMuscleDamagePerPoint = 4.0
  val DefaultScrapeThreshold = 4.0
  val DefaultScrapePerPoint = 4.0
  val DefaultDislocationThreshold = 8.0
  val DefaultFractureThreshold = 10.0
  val DefaultFractureBaseRecoveryTicks = 24000
  val DefaultFallBleedingRatePerWound = 0.5
  val DefaultFallPainPerPoint = 6.0
  val DefaultFracturePain = 50.0
  val DefaultDislocationPain = 30.0

  /** Compiled defaults used when no datapack entry matches the victim. */
  val Fallback: FallRulesData = FallRulesData(HolderSet.empty[EntityType[?]]())

  // The primary codec writes every tuning value so generated defaults are self-documenting. The
  // alternative accepts concise datapack entries and supplies the compiled defaults on decode.
  private val FullCodec: Codec[FallRulesData] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        RegistryCodecs
          .homogeneousList(Registries.ENTITY_TYPE)
          .fieldOf("entities")
          .forGetter(_.entities),
        GameplayCodecs.NonNegativeDouble
          .fieldOf("muscle_damage_per_point")
          .forGetter(_.muscleDamagePerPoint),
        GameplayCodecs.NonNegativeDouble.fieldOf("scrape_threshold").forGetter(_.scrapeThreshold),
        GameplayCodecs.NonNegativeDouble.fieldOf("scrape_per_point").forGetter(_.scrapePerPoint),
        GameplayCodecs.NonNegativeDouble
          .fieldOf("dislocation_threshold")
          .forGetter(_.dislocationThreshold),
        GameplayCodecs.NonNegativeDouble
          .fieldOf("fracture_threshold")
          .forGetter(_.fractureThreshold),
        ExtraCodecs.NON_NEGATIVE_INT
          .fieldOf("fracture_base_recovery_ticks")
          .forGetter(_.fractureBaseRecoveryTicks),
        GameplayCodecs.NonNegativeDouble
          .fieldOf("fall_bleeding_rate_per_wound")
          .forGetter(_.fallBleedingRatePerWound),
        GameplayCodecs.NonNegativeDouble
          .fieldOf("fall_pain_per_point")
          .forGetter(_.fallPainPerPoint),
        GameplayCodecs.NonNegativeDouble.fieldOf("fracture_pain").forGetter(_.fracturePain),
        GameplayCodecs.NonNegativeDouble
          .fieldOf("dislocation_pain")
          .forGetter(_.dislocationPain),
        INT.optionalFieldOf("priority", 0).forGetter(_.priority)
      )
      .apply(instance, FallRulesData.apply)
  )

  private val DefaultsCodec: Codec[FallRulesData] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        RegistryCodecs
          .homogeneousList(Registries.ENTITY_TYPE)
          .fieldOf("entities")
          .forGetter(_.entities),
        GameplayCodecs.NonNegativeDouble
          .optionalFieldOf("muscle_damage_per_point", DefaultMuscleDamagePerPoint)
          .forGetter(_.muscleDamagePerPoint),
        GameplayCodecs.NonNegativeDouble
          .optionalFieldOf("scrape_threshold", DefaultScrapeThreshold)
          .forGetter(_.scrapeThreshold),
        GameplayCodecs.NonNegativeDouble
          .optionalFieldOf("scrape_per_point", DefaultScrapePerPoint)
          .forGetter(_.scrapePerPoint),
        GameplayCodecs.NonNegativeDouble
          .optionalFieldOf("dislocation_threshold", DefaultDislocationThreshold)
          .forGetter(_.dislocationThreshold),
        GameplayCodecs.NonNegativeDouble
          .optionalFieldOf("fracture_threshold", DefaultFractureThreshold)
          .forGetter(_.fractureThreshold),
        ExtraCodecs.NON_NEGATIVE_INT
          .optionalFieldOf("fracture_base_recovery_ticks", DefaultFractureBaseRecoveryTicks)
          .forGetter(_.fractureBaseRecoveryTicks),
        GameplayCodecs.NonNegativeDouble
          .optionalFieldOf("fall_bleeding_rate_per_wound", DefaultFallBleedingRatePerWound)
          .forGetter(_.fallBleedingRatePerWound),
        GameplayCodecs.NonNegativeDouble
          .optionalFieldOf("fall_pain_per_point", DefaultFallPainPerPoint)
          .forGetter(_.fallPainPerPoint),
        GameplayCodecs.NonNegativeDouble
          .optionalFieldOf("fracture_pain", DefaultFracturePain)
          .forGetter(_.fracturePain),
        GameplayCodecs.NonNegativeDouble
          .optionalFieldOf("dislocation_pain", DefaultDislocationPain)
          .forGetter(_.dislocationPain),
        INT.optionalFieldOf("priority", 0).forGetter(_.priority)
      )
      .apply(instance, FallRulesData.apply)
  )

  val Codec: Codec[FallRulesData] = withAlternative(
    FullCodec,
    DefaultsCodec
  )
}

/** Per-entity hit geometry and weighted fallback distribution. */
final case class HitLocationData(
    entities: HolderSet[EntityType[?]],
    legsBelow: Double,
    headAbove: Double,
    fallbackWeights: Map[BodyPart, Double],
    priority: Integer
)

object HitLocationData {
  val DefaultLegsBelow = 0.35
  val DefaultHeadAbove = 1.0
  val DefaultFallbackWeights: Map[BodyPart, Double] = Map(
    BodyPart.Torso -> 0.5,
    BodyPart.Head -> 0.1,
    BodyPart.ArmLeft -> 0.1,
    BodyPart.ArmRight -> 0.1,
    BodyPart.LegLeft -> 0.1,
    BodyPart.LegRight -> 0.1
  )

  /** Compiled defaults used when no datapack entry matches the victim. */
  val Fallback: HitLocationData = HitLocationData(
    HolderSet.empty[EntityType[?]](),
    DefaultLegsBelow,
    DefaultHeadAbove,
    DefaultFallbackWeights,
    0
  )

  private val WeightsCodec: Codec[Map[BodyPart, Double]] = unboundedMap(
    BodyPart.Codec,
    GameplayCodecs.NonNegativeDouble
  )
    .xmap(_.asScala.toMap, _.asJava)
    .validate(weights => {
      val missing = BodyPart.values.toSet -- weights.keySet
      val total = weights.values.sum
      if (missing.nonEmpty) {
        DataResult.error(() => s"fallback_weights is missing: ${missing.map(_.id).mkString(", ")}")
      } else if (!total.isFinite || total > 1.0 + 1.0e-9) {
        DataResult.error(() => s"fallback_weights must sum to at most 1.0, got $total")
      } else DataResult.success(weights)
    })

  val Codec: Codec[HitLocationData] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        RegistryCodecs
          .homogeneousList(Registries.ENTITY_TYPE)
          .fieldOf("entities")
          .forGetter(_.entities),
        GameplayCodecs.UnitDouble.fieldOf("legs_below").forGetter(_.legsBelow),
        GameplayCodecs.NonNegativeDouble.fieldOf("head_above").forGetter(_.headAbove),
        WeightsCodec.fieldOf("fallback_weights").forGetter(_.fallbackWeights),
        INT.optionalFieldOf("priority", 0).forGetter(_.priority)
      )
      .apply(instance, HitLocationData.apply)
  )
}
