package dev.krysztal.casualtiesbelow.item

import java.lang.Double as JDouble
import java.lang.Long as JLong

import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.Identifier
import net.minecraft.util.ExtraCodecs

import dev.krysztal.casualtiesbelow.internal.TypeAlias.JDouble

/** A drawn syringe dose. The source liquid determines its injection side effects, while
  * `opioidDose` stores the true sampled dose even when the item deliberately hides it.
  */
final case class SyringeContents private[casualtiesbelow] (
    liquid: Identifier,
    droplets: Long,
    opioidDose: Double
)

object SyringeContents {
  private val NonNegativeFiniteDouble: Codec[JDouble] =
    com.mojang.serialization.Codec.DOUBLE.validate(value =>
      if (value.doubleValue().isFinite && value.doubleValue() >= 0.0) DataResult.success(value)
      else DataResult.error(() => s"Value must be a finite non-negative number: $value")
    )

  val Codec: Codec[SyringeContents] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        Identifier.CODEC.fieldOf("liquid").forGetter(_.liquid),
        ExtraCodecs.POSITIVE_LONG
          .fieldOf("amount")
          .forGetter(contents => JLong.valueOf(contents.droplets)),
        NonNegativeFiniteDouble
          .fieldOf("opioid_dose")
          .forGetter(contents => JDouble.valueOf(contents.opioidDose))
      )
      .apply(
        instance,
        (liquid, amount, dose) => SyringeContents(liquid, amount.longValue(), dose.doubleValue())
      )
  )
}
