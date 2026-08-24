package dev.krysztal.casualtiesbelow.damage

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
import dev.krysztal.casualtiesbelow.config.FormulaConfigValue

import com.ezylang.evalex.Expression
import com.google.gson.JsonElement
import com.google.gson.JsonParser

/** Datapack-driven per-item armor protection overrides (JSON files under
  * `data/<namespace>/casualtiesbelow/armor_protection/`), layered on top of the `[armor]` config
  * formulas: an entry matching the equipped piece replaces the formula for the factors it defines,
  * and unmatched pieces fall back to the config entirely.
  *
  * Each file is one override entry:
  *
  * {{{
  * {
  *   "items": ["minecraft:leather_chestplate"],   // item ids; at least one of items/tag
  *   "tag": "mypack:cloth_armor",                  // optional item tag, checked live
  *   "skin_factor": "0.3",                         // optional constant or EvalEx formula
  *   "muscle_factor": "0.85"                       // optional; omitted factors fall back to config
  * }
  * }}}
  *
  * Factor expressions see the same variables as the config formulas (`armor`, `toughness`, and
  * `skinFactor` for the muscle factor). Entries intentionally also apply to pieces with zero armor
  * value (e.g. elytra) — identity, not stats, is the point of this layer. Reloaded with `/reload`;
  * invalid entries are logged and skipped, leaving the config fallback in place.
  */
object ArmorProtectionOverrides
    extends SimplePreparableReloadListener[Map[Identifier, JsonElement]] {

  private val Lister = FileToIdConverter.json("casualtiesbelow/armor_protection")

  /** A parsed override: matched by item id or by live tag membership. The factor source strings are
    * retained alongside the compiled expressions so the entry can be shared with clients (see
    * `GameplayDataSnapshot`).
    */
  final case class Entry(
      items: Set[Identifier],
      tag: Option[TagKey[Item]],
      skinFactor: Option[Expression],
      muscleFactor: Option[Expression],
      skinSource: Option[String],
      muscleSource: Option[String]
  ) {

    /** Whether this entry applies to [stack]. */
    def appliesTo(stack: ItemStack): Boolean = {
      Option(BuiltInRegistries.ITEM.getKey(stack.getItem)).exists(items.contains) ||
      tag.exists(stack.is(_))
    }
  }

  @volatile private var entries: List[Entry] = List.empty

  /** All loaded entries, in file order. */
  def allEntries: List[Entry] = entries

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
            CasualtiesBelow.Logger.warn("Unable to read armor protection override {}: {}", id, e)
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
    CasualtiesBelow.Logger.info("Loaded {} armor protection overrides", entries.size)
  }

  private def parseEntry(id: Identifier, json: JsonElement): Option[Entry] = {
    def fail(reason: String): None.type = {
      CasualtiesBelow.Logger.warn("Ignoring armor protection override {}: {}", id, reason)
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

    (parsedItems, parsedTag) match {
      case (Failure(e), _)                => fail(s"invalid items list: ${e.getMessage}")
      case (_, Failure(e))                => fail(s"invalid tag: ${e.getMessage}")
      case (Success(items), Success(tag)) =>
        if (items.isEmpty && tag.isEmpty) return fail("an entry needs at least one of items/tag")

        val skin = parseFactor(id, obj, "skin_factor", List("armor", "toughness"))
        val muscle = parseFactor(id, obj, "muscle_factor", List("armor", "toughness", "skinFactor"))
        if (skin.isEmpty && muscle.isEmpty) {
          return fail("neither skin_factor nor muscle_factor defined")
        }

        Some(
          Entry(items, tag, skin.map(_._2), muscle.map(_._2), skin.map(_._1), muscle.map(_._1))
        )
    }
  }

  /** Parses an optional factor field into its source string and compiled expression; absent or
    * invalid fields become `None` (the config formula stays the fallback for that factor).
    */
  private def parseFactor(
      id: Identifier,
      obj: com.google.gson.JsonObject,
      field: String,
      variables: List[String]
  ): Option[(String, Expression)] = {
    if (!obj.has(field)) return None
    val source = obj.get(field).getAsString
    Try {
      val expression = FormulaConfigValue.compile(source, variables)
      expression.evaluate() // fail fast on formulas that cannot evaluate at all
      (source, expression)
    } match {
      case Success(parsed) => Some(parsed)
      case Failure(e)      =>
        CasualtiesBelow.Logger
          .warn("Ignoring {} of armor protection override {}: {}", field, id, e.getMessage)
        None
    }
  }

  /** Evaluates an entry expression; a runtime failure (domain errors, ...) yields `None` so the
    * caller falls back to the config formula. Server thread only — expressions are not thread-safe.
    */
  def evaluate(
      expression: Expression,
      variables: List[String],
      values: List[Double]
  ): Option[Double] = {
    Try {
      variables.lazyZip(values).foreach { (name, value) =>
        expression.`with`(name, value)
      }
      expression.evaluate().getNumberValue().doubleValue()
    } match {
      case Success(value) => Some(value)
      case Failure(e)     =>
        CasualtiesBelow.Logger.warn("Armor protection formula evaluation failed: {}", e.getMessage)
        None
    }
  }
}
