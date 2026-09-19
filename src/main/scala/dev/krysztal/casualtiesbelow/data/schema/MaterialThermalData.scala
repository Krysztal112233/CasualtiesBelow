package dev.krysztal.casualtiesbelow.data.schema

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/** Per-material thermal coefficients for the body-temperature system.
  *
  * Entries are keyed by equipment-asset id: an armor stack's `EQUIPPABLE` component carries an
  * `asset_id` (`ArmorMaterial.assetId`, e.g. `minecraft:leather`), and the datapack file
  * `data/<namespace>/casualtiesbelow/material_thermal/<path>.json` applies to every stack whose
  * asset id is `<namespace>:<path>`. Equipment assets are the stable identity vanilla attaches to
  * armor materials (modded materials register their own), unlike armor items which several may
  * share one material.
  *
  * All coefficients are fractions in [0, 1]: `insulation` weakens the environment's pull on core
  * temperature (keeps heat exchange out), `dissipationBlock` weakens the body's active cooling
  * (evaporation and overheated re-dissipation), and `fireResistance` reduces direct fire/lava
  * contact heat (netherite's "doesn't burn" extension, independent of the insulation system).
  */
final case class MaterialThermalData(
    insulation: Double,
    dissipationBlock: Double,
    fireResistance: Double
)

object MaterialThermalData {

  /** Fallback for materials without an entry: thermally neutral. */
  val Zero: MaterialThermalData = MaterialThermalData(0.0, 0.0, 0.0)

  val Codec: Codec[MaterialThermalData] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        GameplayCodecs.UnitDouble.fieldOf("insulation").forGetter(_.insulation),
        GameplayCodecs.UnitDouble.fieldOf("dissipation_block").forGetter(_.dissipationBlock),
        GameplayCodecs.UnitDouble
          .optionalFieldOf("fire_resistance", 0.0)
          .forGetter(_.fireResistance)
      )
      .apply(instance, MaterialThermalData.apply)
  )
}
