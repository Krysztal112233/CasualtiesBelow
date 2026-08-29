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
import com.mojang.serialization.DataResult
import com.mojang.serialization.MapCodec
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
private[casualtiesbelow] object GameplayCodecs {
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

  val PositiveUnitDouble: Codec[Double] = Codec.DOUBLE.comapFlatMap(
    value =>
      if (JDouble.isFinite(value) && value > 0.0 && value <= 1.0)
        DataResult.success(value)
      else DataResult.error(() => s"Expected a finite number in (0, 1], got $value"),
    identity
  )

  /** Decodes an absent field to `defaultValue` while always encoding the field. */
  def defaultedField[A](codec: Codec[A], name: String, defaultValue: A): MapCodec[A] =
    MapCodec.of(codec.fieldOf(name), codec.optionalFieldOf(name, defaultValue))

  val BodyPartWeights: Codec[Map[BodyPart, Double]] = unboundedMap(
    BodyPart.Codec,
    NonNegativeDouble
  )
    .xmap(_.asScala.toMap, _.asJava)
    .validate(weights => {
      val missing = BodyPart.values.toSet -- weights.keySet
      val total = weights.values.sum
      if (missing.nonEmpty) {
        DataResult.error(() => s"weights is missing: ${missing.map(_.id).mkString(", ")}")
      } else if (!total.isFinite || total > 1.0 + 1.0e-9) {
        DataResult.error(() => s"weights must sum to at most 1.0, got $total")
      } else DataResult.success(weights)
    })
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
    weights: Optional[Map[BodyPart, Double]],
    priority: Integer
)

object WoundRuleData {
  val DefaultWeights: Map[BodyPart, Double] = Map(
    BodyPart.Torso -> 0.5,
    BodyPart.Head -> 0.1,
    BodyPart.ArmLeft -> 0.1,
    BodyPart.ArmRight -> 0.1,
    BodyPart.LegLeft -> 0.1,
    BodyPart.LegRight -> 0.1
  )
  val DefaultFallWeights: Map[BodyPart, Double] = Map(
    BodyPart.Head -> 0.0,
    BodyPart.Torso -> 0.0,
    BodyPart.ArmLeft -> 0.0,
    BodyPart.ArmRight -> 0.0,
    BodyPart.LegLeft -> 0.5,
    BodyPart.LegRight -> 0.5
  )

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
        GameplayCodecs.BodyPartWeights.optionalFieldOf("weights").forGetter(_.weights),
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

/** Per-entity fall pairing, threshold-spill, and condition rules. Standard tissue and impact pain
  * coefficients live in the classified fall wound profile.
  */
final case class FallRulesData(
    entities: HolderSet[EntityType[?]],
    pairedFraction: Double = FallRulesData.DefaultPairedFraction,
    secondaryThreshold: Double = FallRulesData.DefaultSecondaryThreshold,
    secondaryFraction: Double = FallRulesData.DefaultSecondaryFraction,
    dislocationThreshold: Double = FallRulesData.DefaultDislocationThreshold,
    fractureThreshold: Double = FallRulesData.DefaultFractureThreshold,
    fractureBaseRecoveryTicks: Integer = FallRulesData.DefaultFractureBaseRecoveryTicks,
    fracturePain: Double = FallRulesData.DefaultFracturePain,
    dislocationPain: Double = FallRulesData.DefaultDislocationPain,
    priority: Integer = 0
)

object FallRulesData {
  val DefaultPairedFraction = 1.0
  val DefaultSecondaryThreshold = 8.0
  val DefaultSecondaryFraction = 0.5
  val DefaultDislocationThreshold = 8.0
  val DefaultFractureThreshold = 10.0
  val DefaultFractureBaseRecoveryTicks = 24000
  val DefaultFracturePain = 50.0
  val DefaultDislocationPain = 30.0

  /** Compiled defaults used when no datapack entry matches the victim. */
  val Fallback: FallRulesData = FallRulesData(HolderSet.empty[EntityType[?]]())

  private val DefaultsCodec: Codec[FallRulesData] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        RegistryCodecs
          .homogeneousList(Registries.ENTITY_TYPE)
          .fieldOf("entities")
          .forGetter(_.entities),
        GameplayCodecs
          .defaultedField(
            GameplayCodecs.PositiveUnitDouble,
            "paired_fraction",
            DefaultPairedFraction
          )
          .forGetter(_.pairedFraction),
        GameplayCodecs
          .defaultedField(
            GameplayCodecs.NonNegativeDouble,
            "secondary_threshold",
            DefaultSecondaryThreshold
          )
          .forGetter(_.secondaryThreshold),
        GameplayCodecs
          .defaultedField(
            GameplayCodecs.PositiveUnitDouble,
            "secondary_fraction",
            DefaultSecondaryFraction
          )
          .forGetter(_.secondaryFraction),
        GameplayCodecs
          .defaultedField(
            GameplayCodecs.NonNegativeDouble,
            "dislocation_threshold",
            DefaultDislocationThreshold
          )
          .forGetter(_.dislocationThreshold),
        GameplayCodecs
          .defaultedField(
            GameplayCodecs.NonNegativeDouble,
            "fracture_threshold",
            DefaultFractureThreshold
          )
          .forGetter(_.fractureThreshold),
        GameplayCodecs
          .defaultedField(
            ExtraCodecs.NON_NEGATIVE_INT,
            "fracture_base_recovery_ticks",
            DefaultFractureBaseRecoveryTicks
          )
          .forGetter(_.fractureBaseRecoveryTicks),
        GameplayCodecs
          .defaultedField(
            GameplayCodecs.NonNegativeDouble,
            "fracture_pain",
            DefaultFracturePain
          )
          .forGetter(_.fracturePain),
        GameplayCodecs
          .defaultedField(
            GameplayCodecs.NonNegativeDouble,
            "dislocation_pain",
            DefaultDislocationPain
          )
          .forGetter(_.dislocationPain),
        INT.optionalFieldOf("priority", 0).forGetter(_.priority)
      )
      .apply(instance, FallRulesData.apply)
  )

  val Codec: Codec[FallRulesData] = DefaultsCodec
}

/** Per-entity hit geometry bands. */
final case class HitLocationData(
    entities: HolderSet[EntityType[?]],
    legsBelow: Double,
    headAbove: Double,
    priority: Integer
)

object HitLocationData {
  val DefaultLegsBelow = 0.35
  val DefaultHeadAbove = 1.0

  /** Compiled defaults used when no datapack entry matches the victim. */
  val Fallback: HitLocationData = HitLocationData(
    HolderSet.empty[EntityType[?]](),
    DefaultLegsBelow,
    DefaultHeadAbove,
    0
  )

  val Codec: Codec[HitLocationData] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        RegistryCodecs
          .homogeneousList(Registries.ENTITY_TYPE)
          .fieldOf("entities")
          .forGetter(_.entities),
        GameplayCodecs.UnitDouble.fieldOf("legs_below").forGetter(_.legsBelow),
        GameplayCodecs.NonNegativeDouble.fieldOf("head_above").forGetter(_.headAbove),
        INT.optionalFieldOf("priority", 0).forGetter(_.priority)
      )
      .apply(instance, HitLocationData.apply)
  )
}
