package dev.krysztal.casualtiesbelow.datagen

import java.util.concurrent.CompletableFuture

import com.mojang.serialization.Codec
import net.minecraft.core.HolderLookup
import net.minecraft.data.CachedOutput
import net.minecraft.data.DataProvider
import net.minecraft.data.PackOutput
import net.minecraft.resources.Identifier

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.data.schema.AdrenalineRuleData
import dev.krysztal.casualtiesbelow.data.schema.ArmorProtectionData
import dev.krysztal.casualtiesbelow.data.schema.DiscomfortData
import dev.krysztal.casualtiesbelow.data.schema.FoodImmuneData
import dev.krysztal.casualtiesbelow.data.schema.HitLocationData
import dev.krysztal.casualtiesbelow.data.schema.WoundProfile
import dev.krysztal.casualtiesbelow.data.schema.WoundRuleData

/** Writes built-in keyed gameplay data to the same paths consumed by the reload listeners. */
final class GameplayDataProvider(
    output: FabricPackOutput,
    registries: CompletableFuture[HolderLookup.Provider]
) extends DataProvider {

  override def run(cache: CachedOutput): CompletableFuture[?] = {
    registries.thenCompose { lookup =>
      val writes =
        saveAll(
          cache,
          lookup,
          "wound_profile",
          WoundProfile.Codec,
          CasualtiesBelowDataDefaults.WoundProfiles
        ) ++
          saveAll(
            cache,
            lookup,
            "wound_rule",
            WoundRuleData.Codec,
            CasualtiesBelowDataDefaults.woundRules(lookup)
          ) ++
          saveAll(
            cache,
            lookup,
            "adrenaline_rule",
            AdrenalineRuleData.Codec,
            CasualtiesBelowDataDefaults.adrenalineRules(lookup)
          ) ++
          saveAll(
            cache,
            lookup,
            "armor_protection",
            ArmorProtectionData.Codec,
            CasualtiesBelowDataDefaults.ArmorProtection
          ) ++
          saveAll(
            cache,
            lookup,
            "discomfort",
            DiscomfortData.Codec,
            CasualtiesBelowDataDefaults.Discomfort
          ) ++
          saveAll(
            cache,
            lookup,
            "food_immune/item",
            FoodImmuneData.Codec,
            CasualtiesBelowDataDefaults.FoodImmuneItems
          ) ++
          saveAll(
            cache,
            lookup,
            "food_immune/tag",
            FoodImmuneData.Codec,
            CasualtiesBelowDataDefaults.FoodImmuneTags
          ) ++
          saveAll(
            cache,
            lookup,
            "hit_location",
            HitLocationData.Codec,
            CasualtiesBelowDataDefaults.HitLocations
          )

      CompletableFuture.allOf(writes*)
    }
  }

  private def saveAll[T](
      cache: CachedOutput,
      registries: HolderLookup.Provider,
      segment: String,
      codec: Codec[T],
      entries: Map[Identifier, T]
  ): List[CompletableFuture[?]] = {
    val paths = output.createPathProvider(
      PackOutput.Target.DATA_PACK,
      s"${CasualtiesBelow.ModId}/$segment"
    )
    entries.toList.map { (id, value) =>
      DataProvider.saveStable(cache, registries, codec, value, paths.json(id))
    }
  }

  override def getName(): String = "Casualties: Below gameplay data"
}
