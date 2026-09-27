package dev.krysztal.casualtiesbelow.api.item.datacomponent

import java.lang.Double as JDouble

import com.mojang.serialization.Codec as MCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi

import io.netty.buffer.ByteBuf

/** A food's one-shot body-temperature effect, recorded as the public data component
  * `casualtiesbelow:food_temperature`.
  *
  * The record is pure data: the mod's consumption hook applies an immediate core-temperature pulse
  * of `deltaCelsius` °C on eat (positive warms, negative cools), clamped to the temperature axis,
  * and the equilibrium pull then decays it naturally. Servings stack additively.
  *
  * Being a real component, the record is interop surface: attach it to any item in code via
  * `DefaultItemComponentEvents`, per stack via `/give` (e.g.
  * `minecraft:mushroom_stew[casualtiesbelow:food_temperature={delta_celsius:0.8}]`), or from data
  * packs through recipe results and loot `set_components`.
  */
final case class FoodTemperature(deltaCelsius: Double)

object FoodTemperature {
  val Codec: MCodec[FoodTemperature] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        MCodec.DOUBLE
          .fieldOf("delta_celsius")
          .forGetter(_.deltaCelsius)
      )
      .apply(instance, (delta: JDouble) => FoodTemperature(delta.doubleValue()))
  )

  /** Explicit network codec: keeps client sync of the component efficient; omitting it falls back
    * to a generic (codec-based, NBT-flavored) sync.
    */
  val StreamCodec: StreamCodec[ByteBuf, FoodTemperature] =
    ByteBufCodecs.DOUBLE.map(delta => FoodTemperature(delta), value => value.deltaCelsius)

  val Component: DataComponentType[FoodTemperature] = DataComponentType
    .builder[FoodTemperature]()
    .persistent(Codec)
    .networkSynchronized(StreamCodec)
    .build()

  def register(): Unit = {
    Registry.register(
      BuiltInRegistries.DATA_COMPONENT_TYPE,
      CasualtiesBelowApi.id("food_temperature"),
      Component
    )
  }
}
