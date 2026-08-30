package dev.krysztal.casualtiesbelow.data.schema

import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.Identifier

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi

/** Datapack schema for one linear wound response. Runtime matching and compiled rules are internal.
  */
final case class WoundProfile(
    skinPerPoint: Double,
    musclePerPoint: Double,
    bleedRatePerWound: Double,
    painPerPoint: Double,
    profileType: Identifier
)

object WoundProfile {
  val LinearType: Identifier = CasualtiesBelowApi.id("linear")

  def linear(
      skinPerPoint: Double,
      musclePerPoint: Double,
      bleedRatePerWound: Double,
      painPerPoint: Double
  ): WoundProfile = {
    WoundProfile(
      skinPerPoint,
      musclePerPoint,
      bleedRatePerWound,
      painPerPoint,
      LinearType
    )
  }

  private val RawCodec: Codec[WoundProfile] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        Identifier.CODEC.fieldOf("type").forGetter(_.profileType),
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

  val Codec: Codec[WoundProfile] = RawCodec.validate(profile =>
    if (profile.profileType == LinearType) DataResult.success(profile)
    else DataResult.error(() => s"Unknown wound profile type: ${profile.profileType}")
  )
}
