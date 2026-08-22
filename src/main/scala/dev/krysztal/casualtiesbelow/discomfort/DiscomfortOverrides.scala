package dev.krysztal.casualtiesbelow.discomfort

import java.io.InputStreamReader

import scala.jdk.CollectionConverters.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.Using

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.FileToIdConverter
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.tags.TagKey
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

import dev.krysztal.casualtiesbelow.CasualtiesBelow

import com.google.gson.JsonElement
import com.google.gson.JsonParser

/** Datapack-driven per-item discomfort overrides (JSON files under
  * `data/<namespace>/casualtiesbelow/discomfort/`), layered on top of the tier tags: an entry
  * matching the stack decides its discomfort mean, and unmatched stacks fall back to the tier tags.
  *
  * Each file is one override entry:
  *
  * {{{
  * {
  *   "items": ["mymod:mystery_meat"],   // item ids; at least one of items/tag
  *   "tag": "mypack:raw_delicacies",    // optional item tag, checked live
  *   "mean": 45.0                        // exactly one of mean/level
  * }
  * }}}
  *
  * or
  *
  * {{{
  * { "tag": "minecraft:fish", "level": 1 }
  * }}}
  *
  * `level` (1-3) reassigns the tier and prices it from the `[discomfort]` config means; `mean` sets
  * the value outright. Declaring both (or neither) is rejected — ambiguity about who wins is worse
  * than a skipped file. Spread is never per-item: it scales with the mean globally
  * ([[dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig.DiscomfortSpreadFraction]]), so the
  * distribution shape stays consistent across all food.
  *
  * Reloaded with `/reload`; invalid entries are logged and skipped, leaving the tags in place.
  */
object DiscomfortOverrides extends SimplePreparableReloadListener[Map[Identifier, JsonElement]] {

  private val Lister = FileToIdConverter.json("casualtiesbelow/discomfort")

  /** A parsed override: matched by item id or by live tag membership; carries either a tier
    * reassignment or an explicit mean.
    */
  final case class Entry(
      items: Set[Identifier],
      tag: Option[TagKey[Item]],
      level: Option[Int],
      mean: Option[Double]
  ) {

    /** Whether this entry applies to [stack]. */
    def appliesTo(stack: ItemStack): Boolean = {
      items.contains(BuiltInRegistries.ITEM.getKey(stack.getItem)) ||
      tag.exists(stack.is(_))
    }
  }

  @volatile private var entries: List[Entry] = List.empty

  /** The highest-priority entry applying to [stack], if any (files are considered in identifier
    * order for determinism).
    */
  def forStack(stack: ItemStack): Option[Entry] = entries.find(_.appliesTo(stack))

  override def prepare(
      manager: ResourceManager,
      profiler: ProfilerFiller
  ): Map[Identifier, JsonElement] = {
    Lister
      .listMatchingResources(manager)
      .asScala
      .flatMap { (id, resource) =>
        Using(InputStreamReader(resource.open()))(JsonParser.parseReader) match {
          case Success(json) => Some(id -> json)
          case Failure(e)    =>
            CasualtiesBelow.Logger.warn("Unable to read discomfort override {}: {}", id, e)
            None
        }
      }
      .toMap
  }

  override def apply(
      prepared: Map[Identifier, JsonElement],
      manager: ResourceManager,
      profiler: ProfilerFiller
  ): Unit = {
    entries = prepared.toList.sortBy(_._1.toString).flatMap { (id, json) =>
      parseEntry(id, json)
    }
    CasualtiesBelow.Logger.info("Loaded {} discomfort overrides", entries.size)
  }

  private def parseEntry(id: Identifier, json: JsonElement): Option[Entry] = {
    def fail(reason: String): None.type = {
      CasualtiesBelow.Logger.warn("Ignoring discomfort override {}: {}", id, reason)
      None
    }

    if (!json.isJsonObject) return fail("not a JSON object")
    val obj = json.getAsJsonObject

    val parsedItems =
      if (obj.has("items")) {
        Try(obj.getAsJsonArray("items").asScala.map(e => Identifier.parse(e.getAsString)).toSet)
      } else Success(Set.empty)
    val parsedTag =
      if (obj.has("tag")) {
        Try(Some(TagKey.create(Registries.ITEM, Identifier.parse(obj.get("tag").getAsString))))
      } else Success(None)
    val parsedLevel =
      if (obj.has("level")) Try(Some(obj.get("level").getAsInt)) else Success(None)
    val parsedMean =
      if (obj.has("mean")) Try(Some(obj.get("mean").getAsDouble)) else Success(None)

    (parsedItems, parsedTag, parsedLevel, parsedMean) match {
      case (Failure(e), _, _, _) => fail(s"invalid items list: ${e.getMessage}")
      case (_, Failure(e), _, _) => fail(s"invalid tag: ${e.getMessage}")
      case (_, _, Failure(e), _) => fail(s"invalid level: ${e.getMessage}")
      case (_, _, _, Failure(e)) => fail(s"invalid mean: ${e.getMessage}")
      case (Success(items), Success(tag), Success(level), Success(mean)) =>
        if (items.isEmpty && tag.isEmpty) return fail("an entry needs at least one of items/tag")
        if (level.isDefined == mean.isDefined) {
          return fail("exactly one of level/mean must be defined")
        }
        if (level.exists(l => l < 1 || l > 3)) return fail("level must be between 1 and 3")
        if (mean.exists(m => !m.isFinite || m < 0.0)) {
          return fail("mean must be a finite, non-negative number")
        }

        Some(Entry(items, tag, level, mean))
    }
  }
}
