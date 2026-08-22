package dev.krysztal.casualtiesbelow.datagen

import java.util.concurrent.CompletableFuture

import net.minecraft.data.CachedOutput
import net.minecraft.data.DataProvider
import net.minecraft.data.PackOutput
import net.minecraft.resources.Identifier

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput

import dev.krysztal.casualtiesbelow.CasualtiesBelow

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Generates the mod's built-in armor protection overrides
  * (`data/casualtiesbelow/casualtiesbelow/armor_protection/`), giving each vanilla armor material a
  * distinct protection personality on top of the `[armor]` config formulas (which remain the
  * fallback for armor without an entry, e.g. modded sets):
  *
  *   - '''leather''' — padding: claws and teeth still break skin, but blunt impact is absorbed well
  *   - '''chainmail''' — rings: superb against slashes and bites, transmits blunt trauma fully
  *   - '''iron''' — balanced (mirrors the global formula shape)
  *   - '''diamond''' — slightly better than iron all around
  *   - '''netherite''' — the strongest on both axes
  *
  * Boots are intentionally absent (they map to no body part); turtle shells and other sets keep the
  * config formulas.
  */
final class ArmorProtectionOverrideProvider(output: FabricPackOutput) extends DataProvider {

  /** One material's override: the armor pieces it covers and the two factor formulas (variables
    * `armor`, `toughness`, and `skinFactor` for the muscle factor — see ArmorProtectionOverrides).
    */
  private final case class ArmorSet(
      name: String,
      pieces: List[String],
      skinFactor: String,
      muscleFactor: String
  )

  private val sets = List(
    ArmorSet(
      "leather",
      pieces("leather"),
      "max(0.05, 1 - armor*0.05)",
      "1 - (1-skinFactor)*0.9"
    ),
    ArmorSet(
      "chainmail",
      pieces("chainmail"),
      "max(0.05, 1 - armor*0.16)",
      "1 - (1-skinFactor)*0.1"
    ),
    ArmorSet(
      "iron",
      pieces("iron"),
      "max(0.05, 1 - armor*0.1)",
      "1 - (1-skinFactor)*0.5"
    ),
    ArmorSet(
      "diamond",
      pieces("diamond"),
      "max(0.05, 1 - armor*0.11 - toughness*0.02)",
      "1 - (1-skinFactor)*0.55"
    ),
    ArmorSet(
      "netherite",
      pieces("netherite"),
      "max(0.05, 1 - armor*0.12 - toughness*0.03)",
      "1 - (1-skinFactor)*0.65"
    )
  )

  private def pieces(material: String): List[String] = {
    List("helmet", "chestplate", "leggings").map(slot => s"minecraft:${material}_$slot")
  }

  override def run(cache: CachedOutput): CompletableFuture[?] = {
    val paths = output.createPathProvider(
      PackOutput.Target.DATA_PACK,
      "casualtiesbelow/armor_protection"
    )
    val writes = sets.map { set =>
      val items = new JsonArray
      set.pieces.foreach(items.add)
      val json = new JsonObject
      json.add("items", items)
      json.addProperty("skin_factor", set.skinFactor)
      json.addProperty("muscle_factor", set.muscleFactor)
      DataProvider.saveStable(
        cache,
        json,
        paths.json(Identifier.fromNamespaceAndPath(CasualtiesBelow.ModId, set.name))
      )
    }
    CompletableFuture.allOf(writes*)
  }

  override def getName: String = "Armor protection overrides"
}
