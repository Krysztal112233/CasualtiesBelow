package dev.krysztal.casualtiesbelow.api.wound

import java.util.Optional

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import com.mojang.serialization.Codec as MCodec
import com.mojang.serialization.DataResult
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.Identifier
import net.minecraft.util.ExtraCodecs

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.api.body.LimbCondition
import dev.krysztal.casualtiesbelow.api.data.GameplayCodecs
import dev.krysztal.casualtiesbelow.api.data.WoundRuleData

/** An optional wound side effect that can cauterize existing external bleeding on the same limb.
  * Each accepted wound with positive skin damage rolls once; success removes the configured
  * fraction of the limb's current bleeding rate.
  */
final case class HemostasisData(chance: Double, reductionFraction: Double)

object HemostasisData {
  val Codec: Codec[HemostasisData] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        GameplayCodecs.PositiveUnitDouble.fieldOf("chance").forGetter(_.chance),
        GameplayCodecs.PositiveUnitDouble
          .fieldOf("reduction_fraction")
          .forGetter(_.reductionFraction)
      )
      .apply(instance, HemostasisData.apply)
  )
}

/** One profile contribution made by an application. Multiple contributions let one damage source
  * produce, for example, both a puncture and a burn without re-applying vanilla health damage.
  */
final case class WoundContributionData(
    profile: Identifier,
    severityMultiplier: Double,
    hemostasis: Optional[HemostasisData] = Optional.empty()
)

object WoundContributionData {
  val Codec: Codec[WoundContributionData] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        Identifier.CODEC.fieldOf("profile").forGetter(_.profile),
        GameplayCodecs
          .defaultedField(GameplayCodecs.NonNegativeDouble, "severity_multiplier", 1.0)
          .forGetter(_.severityMultiplier),
        HemostasisData.Codec.optionalFieldOf("hemostasis").forGetter(_.hemostasis)
      )
      .apply(instance, WoundContributionData.apply)
  )

  val NonEmptyListCodec: Codec[List[WoundContributionData]] =
    MCodec
      .list(Codec)
      .xmap(
        _.asScala.toList,
        _.asJava
      )
      .validate(values =>
        if (values.nonEmpty) DataResult.success(values)
        else DataResult.error(() => "wounds must contain at least one profile contribution")
      )
}

/** Where one localized or primary impact lands. The tagged alternatives make invalid combinations
  * such as a fixed part plus random weights unrepresentable.
  */
sealed trait WoundTargetData

final case class HitLocationTargetData(weights: Map[BodyPart, Double]) extends WoundTargetData
final case class FixedTargetData(part: BodyPart) extends WoundTargetData
final case class WeightedTargetData(weights: Map[BodyPart, Double]) extends WoundTargetData

object WoundTargetData {
  val HitLocationType: Identifier = CasualtiesBelow.ofIdentifier("hit_location")
  val FixedType: Identifier = CasualtiesBelow.ofIdentifier("fixed")
  val WeightedType: Identifier = CasualtiesBelow.ofIdentifier("weighted")

  val Default: WoundTargetData = HitLocationTargetData(WoundRuleData.DefaultWeights)

  private val HitLocationCodec: Codec[HitLocationTargetData] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        exactType(HitLocationType).fieldOf("type").forGetter(_ => ()),
        GameplayCodecs.SparseBodyPartWeights
          .optionalFieldOf("weights", WoundRuleData.DefaultWeights)
          .forGetter(_.weights)
      )
      .apply(instance, (_, weights) => HitLocationTargetData(weights))
  )

  private val FixedCodec: Codec[FixedTargetData] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        exactType(FixedType).fieldOf("type").forGetter(_ => ()),
        BodyPart.Codec.fieldOf("part").forGetter(_.part)
      )
      .apply(instance, (_, part) => FixedTargetData(part))
  )

  private val WeightedCodec: Codec[WeightedTargetData] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        exactType(WeightedType).fieldOf("type").forGetter(_ => ()),
        GameplayCodecs.SparseBodyPartWeights.fieldOf("weights").forGetter(_.weights)
      )
      .apply(instance, (_, weights) => WeightedTargetData(weights))
  )

  val Codec: Codec[WoundTargetData] = MCodec
    .either(
      HitLocationCodec,
      MCodec.either(FixedCodec, WeightedCodec)
    )
    .xmap(
      _.map(
        (target: HitLocationTargetData) => target: WoundTargetData,
        _.map(
          (target: FixedTargetData) => target: WoundTargetData,
          (target: WeightedTargetData) => target: WoundTargetData
        )
      ),
      {
        case target: HitLocationTargetData => Either.left(target)
        case target: FixedTargetData       => Either.right(Either.left(target))
        case target: WeightedTargetData    => Either.right(Either.right(target))
      }
    )

  private def exactType(expected: Identifier): Codec[Unit] = Identifier.CODEC.comapFlatMap(
    actual =>
      if (actual == expected) DataResult.success(())
      else DataResult.error(() => s"Expected type '$expected', got '$actual'"),
    _ => expected
  )
}

enum WoundSeverityPolicy(val id: Identifier) {
  case Standard extends WoundSeverityPolicy(CasualtiesBelow.ofIdentifier("standard"))
  case FallImpact extends WoundSeverityPolicy(CasualtiesBelow.ofIdentifier("fall_impact"))
}

object WoundSeverityPolicy {
  private val ById = WoundSeverityPolicy.values.map(value => value.id -> value).toMap

  val Codec: Codec[WoundSeverityPolicy] = Identifier.CODEC.comapFlatMap(
    id =>
      ById
        .get(id)
        .map(DataResult.success)
        .getOrElse(DataResult.error(() => s"Unknown wound severity policy: $id")),
    _.id
  )
}

final case class PairedImpactData(fraction: Double)

object PairedImpactData {
  val Codec: Codec[PairedImpactData] = RecordCodecBuilder.create(instance =>
    instance
      .group(GameplayCodecs.PositiveUnitDouble.fieldOf("fraction").forGetter(_.fraction))
      .apply(instance, PairedImpactData.apply)
  )
}

final case class SpillImpactData(above: Double, count: Int, fraction: Double)

object SpillImpactData {
  val Codec: Codec[SpillImpactData] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        GameplayCodecs.NonNegativeDouble.fieldOf("above").forGetter(_.above),
        ExtraCodecs
          .intRange(1, BodyPart.values.length - 1)
          .optionalFieldOf("count", 1)
          .forGetter(_.count),
        GameplayCodecs.PositiveUnitDouble.fieldOf("fraction").forGetter(_.fraction)
      )
      .apply(
        instance,
        (above, count, fraction) => SpillImpactData(above, count.intValue(), fraction)
      )
  )
}

final case class ConditionStepData(
    condition: LimbCondition,
    atLeast: Double,
    pain: Double,
    baseRecoveryTicks: Optional[Integer]
)

object ConditionStepData {
  private val ConditionCodec: Codec[LimbCondition] =
    MCodec.STRING.comapFlatMap(
      {
        case "fracture"    => DataResult.success(LimbCondition.Fracture)
        case "dislocation" => DataResult.success(LimbCondition.Dislocation)
        case other         => DataResult.error(() => s"Unknown limb condition: $other")
      },
      {
        case LimbCondition.Fracture    => "fracture"
        case LimbCondition.Dislocation => "dislocation"
      }
    )

  private val RawCodec: Codec[ConditionStepData] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        ConditionCodec.fieldOf("condition").forGetter(_.condition),
        GameplayCodecs.NonNegativeDouble.fieldOf("at_least").forGetter(_.atLeast),
        GameplayCodecs.NonNegativeDouble.fieldOf("pain").forGetter(_.pain),
        ExtraCodecs.NON_NEGATIVE_INT
          .optionalFieldOf("base_recovery_ticks")
          .forGetter(_.baseRecoveryTicks)
      )
      .apply(instance, ConditionStepData.apply)
  )

  val Codec: Codec[ConditionStepData] = RawCodec.validate {
    case step @ ConditionStepData(LimbCondition.Fracture, threshold, _, recovery) =>
      if (threshold <= 0.0) {
        DataResult.error(() => "A fracture threshold must be greater than zero")
      } else if (recovery.isEmpty) {
        DataResult.error(() => "A fracture step requires base_recovery_ticks")
      } else DataResult.success(step)
    case step @ ConditionStepData(LimbCondition.Dislocation, _, _, recovery) =>
      if (recovery.isPresent) {
        DataResult.error(() => "A dislocation step cannot define base_recovery_ticks")
      } else DataResult.success(step)
  }
}

/** A typed way to distribute one captured damage event into one or more limb injuries. */
sealed trait WoundApplicationData {
  def wounds: List[WoundContributionData]
}

final case class LocalizedApplicationData(
    wounds: List[WoundContributionData],
    target: WoundTargetData
) extends WoundApplicationData

final case class ScatterApplicationData(
    wounds: List[WoundContributionData],
    minCount: Int,
    maxCount: Int
) extends WoundApplicationData

final case class PairedImpactApplicationData(
    wounds: List[WoundContributionData],
    severityPolicy: WoundSeverityPolicy,
    primary: WoundTargetData,
    paired: Optional[PairedImpactData],
    spill: Optional[SpillImpactData],
    conditionLadder: List[ConditionStepData]
) extends WoundApplicationData

object WoundApplicationData {
  val LocalizedType: Identifier = CasualtiesBelow.ofIdentifier("localized")
  val ScatterType: Identifier = CasualtiesBelow.ofIdentifier("scatter")
  val PairedImpactType: Identifier = CasualtiesBelow.ofIdentifier("paired_impact")

  private val LocalizedCodec: Codec[LocalizedApplicationData] =
    RecordCodecBuilder.create(instance =>
      instance
        .group(
          exactType(LocalizedType).fieldOf("type").forGetter(_ => ()),
          WoundContributionData.NonEmptyListCodec.fieldOf("wounds").forGetter(_.wounds),
          WoundTargetData.Codec
            .optionalFieldOf("target", WoundTargetData.Default)
            .forGetter(_.target)
        )
        .apply(instance, (_, wounds, target) => LocalizedApplicationData(wounds, target))
    )

  private val ScatterRawCodec: Codec[ScatterApplicationData] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        exactType(ScatterType).fieldOf("type").forGetter(_ => ()),
        WoundContributionData.NonEmptyListCodec.fieldOf("wounds").forGetter(_.wounds),
        ExtraCodecs
          .intRange(1, BodyPart.values.length)
          .optionalFieldOf("min_count", 2)
          .forGetter(_.minCount),
        ExtraCodecs
          .intRange(1, BodyPart.values.length)
          .optionalFieldOf("max_count", 3)
          .forGetter(_.maxCount)
      )
      .apply(
        instance,
        (_, wounds, minCount, maxCount) => ScatterApplicationData(wounds, minCount, maxCount)
      )
  )

  private val ScatterCodec: Codec[ScatterApplicationData] = ScatterRawCodec.validate(data =>
    if (data.minCount <= data.maxCount) DataResult.success(data)
    else {
      DataResult.error(() =>
        s"scatter min_count ${data.minCount} exceeds max_count ${data.maxCount}"
      )
    }
  )

  private val ConditionLadderCodec: Codec[List[ConditionStepData]] =
    MCodec
      .list(ConditionStepData.Codec)
      .xmap(_.asScala.toList, _.asJava)
      .validate(validateConditionLadder)

  private val PairedImpactCodec: Codec[PairedImpactApplicationData] =
    RecordCodecBuilder.create(instance =>
      instance
        .group(
          exactType(PairedImpactType).fieldOf("type").forGetter(_ => ()),
          WoundContributionData.NonEmptyListCodec.fieldOf("wounds").forGetter(_.wounds),
          WoundSeverityPolicy.Codec
            .optionalFieldOf("severity_policy", WoundSeverityPolicy.Standard)
            .forGetter(_.severityPolicy),
          WoundTargetData.Codec.fieldOf("primary").forGetter(_.primary),
          PairedImpactData.Codec.optionalFieldOf("paired").forGetter(_.paired),
          SpillImpactData.Codec.optionalFieldOf("spill").forGetter(_.spill),
          ConditionLadderCodec
            .optionalFieldOf("condition_ladder", List.empty)
            .forGetter(_.conditionLadder)
        )
        .apply(
          instance,
          (_, wounds, policy, primary, paired, spill, conditions) =>
            PairedImpactApplicationData(
              wounds,
              policy,
              primary,
              paired,
              spill,
              conditions
            )
        )
    )

  val Codec: Codec[WoundApplicationData] = MCodec
    .either(
      LocalizedCodec,
      MCodec.either(ScatterCodec, PairedImpactCodec)
    )
    .xmap(
      _.map(
        (application: LocalizedApplicationData) => application: WoundApplicationData,
        _.map(
          (application: ScatterApplicationData) => application: WoundApplicationData,
          (application: PairedImpactApplicationData) => application: WoundApplicationData
        )
      ),
      {
        case application: LocalizedApplicationData    => Either.left(application)
        case application: ScatterApplicationData      => Either.right(Either.left(application))
        case application: PairedImpactApplicationData =>
          Either.right(Either.right(application))
      }
    )

  val NonEmptyListCodec: Codec[List[WoundApplicationData]] =
    MCodec
      .list(Codec)
      .xmap(
        _.asScala.toList,
        _.asJava
      )
      .validate(values =>
        if (values.nonEmpty) DataResult.success(values)
        else DataResult.error(() => "applications must contain at least one entry")
      )

  private def validateConditionLadder(
      steps: List[ConditionStepData]
  ): DataResult[List[ConditionStepData]] = {
    val duplicates = steps.groupBy(_.condition).collect {
      case (condition, values) if values.size > 1 =>
        condition
    }
    if (duplicates.nonEmpty) {
      DataResult.error(() => s"condition_ladder repeats: ${duplicates.mkString(", ")}")
    } else if (
      steps.sliding(2).exists {
        case List(first, second) => first.atLeast < second.atLeast
        case _                   => false
      }
    ) {
      DataResult.error(() => "condition_ladder thresholds must be in descending order")
    } else DataResult.success(steps)
  }

  private def exactType(expected: Identifier): Codec[Unit] = Identifier.CODEC.comapFlatMap(
    actual =>
      if (actual == expected) DataResult.success(())
      else DataResult.error(() => s"Expected type '$expected', got '$actual'"),
    _ => expected
  )
}
