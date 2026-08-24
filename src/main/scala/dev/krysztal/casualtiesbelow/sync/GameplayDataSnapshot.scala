package dev.krysztal.casualtiesbelow.sync

import scala.jdk.CollectionConverters.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.CasualtiesBelowTags
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig.WoundProfileConfig
import dev.krysztal.casualtiesbelow.damage.ArmorProtectionOverrides
import dev.krysztal.casualtiesbelow.discomfort.DiscomfortOverrides

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/** Whether an override entry (explicit items or one tag) targets [stack]. */
private def targets(stack: ItemStack, items: List[String], tag: Option[String]): Boolean = {
  Option(BuiltInRegistries.ITEM.getKey(stack.getItem)).exists(key =>
    items.contains(key.toString)
  ) ||
  tag.exists(t => stack.is(TagKey.create(Registries.ITEM, Identifier.parse(t))))
}

/** An armor protection override as shared with clients: the formula SOURCE strings (clients
  * recompile them with EvalEx; compiled expressions are not transferable).
  */
final case class ArmorOverrideData(
    items: List[String],
    tag: Option[String],
    skin: Option[String],
    muscle: Option[String]
) {
  def appliesTo(stack: ItemStack): Boolean = targets(stack, items, tag)
}

/** A discomfort override as shared with clients (level XOR mean, as in the datapack files). */
final case class DiscomfortOverrideData(
    items: List[String],
    tag: Option[String],
    level: Option[Int],
    mean: Option[Double]
) {
  def appliesTo(stack: ItemStack): Boolean = targets(stack, items, tag)
}

/** The server-effective gameplay numbers and override tables.
  *
  * The server is authoritative for all gameplay numbers, but displays (JEI info pages) and
  * client-side prediction need to know what the server will do — mirroring the modern JEI
  * philosophy that a viewer shows the server's truth, not the client's local files. The server
  * sends this snapshot during the configuration phase on join (so it lands before JEI starts, which
  * is triggered by the recipe sync) and again after datapack reloads. Without a received snapshot,
  * [[GameplayDataSnapshot.current]] falls back to the local config and locally visible overrides,
  * which is correct in singleplayer for everything except world-datapack overrides.
  */
final case class GameplayDataSnapshot(
    armorSkinFormula: String,
    armorMuscleFormula: String,
    armorOverrides: List[ArmorOverrideData],
    maxDiscomfort: Double,
    discomfortLevelMeans: List[Double],
    nauseaThreshold: Double,
    refusalThreshold: Double,
    vomitChanceThreshold: Double,
    vomitMinChancePerTick: Double,
    vomitMaxChancePerTick: Double,
    vomitRelief: Double,
    vomitReliefSpreadFraction: Double,
    discomfortOverrides: List[DiscomfortOverrideData],
    /** Wound profile coefficients by profile name: (skin, muscle, bleed, pain) per damage point. */
    wounds: Map[String, (Double, Double, Double, Double)]
) {

  /** Resolves the discomfort mean for [stack] from this snapshot, mirroring `Discomfort.meanOf`:
    * explicit override entries first (a level resolves through this snapshot's tier means), then
    * the tier tags (most severe tier wins). Returns the mean paired with the tier it came from
    * (`None` for free-form means). Component-derived specials (suspicious stew) are the caller's
    * job — they need no snapshot data.
    */
  def discomfortMeanOf(stack: ItemStack): Option[(Double, Option[Int])] = {
    discomfortOverrides.find(_.appliesTo(stack)) match {
      case Some(entry) =>
        entry.level
          .map(l => (tierMean(l, discomfortLevelMeans), Some(l)))
          .orElse(entry.mean.map(m => (m, None)))
      case None =>
        if (stack.is(CasualtiesBelowTags.Discomfort3Items)) {
          Some((discomfortLevelMeans(2), Some(3)))
        } else if (stack.is(CasualtiesBelowTags.Discomfort2Items)) {
          Some((discomfortLevelMeans(1), Some(2)))
        } else if (stack.is(CasualtiesBelowTags.Discomfort1Items)) {
          Some((discomfortLevelMeans(0), Some(1)))
        } else None
    }
  }

  /** The mean of a discomfort tier; out-of-range levels collapse to the most severe tier. */
  private def tierMean(level: Int, means: List[Double]): Double = {
    means.applyOrElse(level - 1, (_: Int) => means.last)
  }

  def toJson: String = {
    def armorOverrideJson(o: ArmorOverrideData): JsonObject = {
      jsonObject(
        "items" -> Option.when(o.items.nonEmpty)(stringArray(o.items)),
        "tag" -> o.tag.map(JsonPrimitive(_)),
        "skin" -> o.skin.map(JsonPrimitive(_)),
        "muscle" -> o.muscle.map(JsonPrimitive(_))
      )
    }

    def discomfortOverrideJson(o: DiscomfortOverrideData): JsonObject = {
      jsonObject(
        "items" -> Option.when(o.items.nonEmpty)(stringArray(o.items)),
        "tag" -> o.tag.map(JsonPrimitive(_)),
        "level" -> o.level.map(JsonPrimitive(_)),
        "mean" -> o.mean.map(JsonPrimitive(_))
      )
    }

    jsonObject(
      "armor" -> Some(
        jsonObject(
          "skinFormula" -> Some(JsonPrimitive(armorSkinFormula)),
          "muscleFormula" -> Some(JsonPrimitive(armorMuscleFormula)),
          "overrides" -> Some(jsonArray(armorOverrides)(armorOverrideJson))
        )
      ),
      "discomfort" -> Some(
        jsonObject(
          "maxValue" -> Some(JsonPrimitive(maxDiscomfort)),
          "levelMeans" -> Some(jsonArray(discomfortLevelMeans)(JsonPrimitive(_))),
          "nausea" -> Some(JsonPrimitive(nauseaThreshold)),
          "refusal" -> Some(JsonPrimitive(refusalThreshold)),
          "vomitChanceThreshold" -> Some(JsonPrimitive(vomitChanceThreshold)),
          "vomitMinChancePerTick" -> Some(JsonPrimitive(vomitMinChancePerTick)),
          "vomitMaxChancePerTick" -> Some(JsonPrimitive(vomitMaxChancePerTick)),
          "vomitRelief" -> Some(JsonPrimitive(vomitRelief)),
          "vomitReliefSpreadFraction" -> Some(JsonPrimitive(vomitReliefSpreadFraction)),
          "overrides" -> Some(jsonArray(discomfortOverrides)(discomfortOverrideJson))
        )
      ),
      "wounds" -> Some(
        jsonObject(
          wounds.toList.map { (name, p) =>
            name -> Some(jsonArray(List(p._1, p._2, p._3, p._4))(JsonPrimitive(_)))
          }*
        )
      )
    ).toString
  }

  /** Builds a JSON array by mapping each element; the only place an array is mutated. */
  private def jsonArray[A](values: Iterable[A])(f: A => JsonElement): JsonArray = {
    val array = new JsonArray
    values.foreach(v => array.add(f(v)))
    array
  }

  private def stringArray(values: Iterable[String]): JsonArray = jsonArray(values)(JsonPrimitive(_))

  /** Builds a JSON object from optional fields; absent values are simply omitted. The only place an
    * object is mutated.
    */
  private def jsonObject(fields: (String, Option[JsonElement])*): JsonObject = {
    val obj = new JsonObject
    fields.foreach { (key, value) => value.foreach(obj.add(key, _)) }
    obj
  }
}

object GameplayDataSnapshot {

  /** The latest snapshot received from the server, if any. Cleared on disconnect. */
  @volatile private var synced: Option[GameplayDataSnapshot] = None

  /** What client-side displays and prediction should read: the server's snapshot when connected,
    * else the local configuration and locally visible (builtin) overrides. Server logic must NOT
    * read this: `synced` belongs to the logical client (singleplayer shares the JVM), so the server
    * always captures the live state via [[capture]] instead.
    */
  def current: GameplayDataSnapshot = synced.getOrElse(capture())

  /** Stores a snapshot received from the server. Malformed payloads are logged and ignored, keeping
    * the previous state.
    */
  def receive(json: String): Unit = {
    Try(fromJson(json)) match {
      case Success(snapshot) => synced = Some(snapshot)
      case Failure(e)        =>
        CasualtiesBelow.Logger.warn("Ignoring malformed gameplay data sync: {}", e.getMessage)
    }
  }

  def clearSynced(): Unit = synced = None

  /** Builds the snapshot from the effective local state (the config plus the loaded override
    * entries). Used both by the server to fill the sync payload and by the client as fallback.
    */
  def capture(): GameplayDataSnapshot = {
    val config = CasualtiesBelowConfig
    GameplayDataSnapshot(
      armorSkinFormula = config.ArmorSkinFactorFormula.spec.get(),
      armorMuscleFormula = config.ArmorMuscleFactorFormula.spec.get(),
      armorOverrides = ArmorProtectionOverrides.allEntries.map { e =>
        ArmorOverrideData(
          e.items.map(_.toString).toList.sorted,
          e.tag.map(_.location.toString),
          e.skinSource,
          e.muscleSource
        )
      },
      maxDiscomfort = config.MaxDiscomfort.get(),
      discomfortLevelMeans = List(
        config.DiscomfortLevel1Mean.get(),
        config.DiscomfortLevel2Mean.get(),
        config.DiscomfortLevel3Mean.get()
      ),
      nauseaThreshold = config.DiscomfortNauseaThreshold.get(),
      refusalThreshold = config.DiscomfortRefusalThreshold.get(),
      vomitChanceThreshold = config.DiscomfortVomitChanceThreshold.get(),
      vomitMinChancePerTick = config.DiscomfortVomitMinChancePerTick.get(),
      vomitMaxChancePerTick = math.max(
        config.DiscomfortVomitMaxChancePerTick.get(),
        config.DiscomfortVomitMinChancePerTick.get()
      ),
      vomitRelief = config.DiscomfortVomitRelief.get(),
      vomitReliefSpreadFraction = config.DiscomfortVomitReliefSpreadFraction.get(),
      discomfortOverrides = DiscomfortOverrides.allEntries.map { e =>
        DiscomfortOverrideData(
          e.items.map(_.toString).toList.sorted,
          e.tag.map(_.location.toString),
          e.level,
          e.mean
        )
      },
      wounds = Map(
        "bite" -> profile(config.BiteWound),
        "cut" -> profile(config.CutWound),
        "blunt" -> profile(config.BluntWound),
        "pierce" -> profile(config.PierceWound),
        "burn" -> profile(config.BurnWound),
        "prick" -> profile(config.PrickWound),
        "blast" -> profile(config.BlastWound)
      )
    )
  }

  private def profile(config: WoundProfileConfig): (Double, Double, Double, Double) = {
    (
      config.skinPerPoint.get(),
      config.musclePerPoint.get(),
      config.bleedRatePerWound.get(),
      config.painPerPoint.get()
    )
  }

  private def fromJson(json: String): GameplayDataSnapshot = {
    def optString(obj: JsonObject, key: String): Option[String] = {
      Option.when(obj.has(key))(obj.get(key).getAsString)
    }
    def optItems(obj: JsonObject): List[String] = {
      if (obj.has("items")) obj.getAsJsonArray("items").asScala.map(_.getAsString).toList
      else List.empty
    }

    val root = JsonParser.parseString(json).getAsJsonObject

    val armor = root.getAsJsonObject("armor")
    val armorOverrides = armor.getAsJsonArray("overrides").asScala.toList.map { el =>
      val e = el.getAsJsonObject
      ArmorOverrideData(
        optItems(e),
        optString(e, "tag"),
        optString(e, "skin"),
        optString(e, "muscle")
      )
    }

    val discomfort = root.getAsJsonObject("discomfort")
    val discomfortOverrides = discomfort.getAsJsonArray("overrides").asScala.toList.map { el =>
      val e = el.getAsJsonObject
      DiscomfortOverrideData(
        optItems(e),
        optString(e, "tag"),
        Option.when(e.has("level"))(e.get("level").getAsInt),
        Option.when(e.has("mean"))(e.get("mean").getAsDouble)
      )
    }

    val wounds = root
      .getAsJsonObject("wounds")
      .entrySet()
      .asScala
      .map { entry =>
        val a = entry.getValue.getAsJsonArray
        entry.getKey -> (
          a.get(0).getAsDouble,
          a.get(1).getAsDouble,
          a.get(2).getAsDouble,
          a.get(3).getAsDouble
        )
      }
      .toMap

    GameplayDataSnapshot(
      armorSkinFormula = armor.get("skinFormula").getAsString,
      armorMuscleFormula = armor.get("muscleFormula").getAsString,
      armorOverrides = armorOverrides,
      maxDiscomfort = discomfort.get("maxValue").getAsDouble,
      discomfortLevelMeans =
        discomfort.getAsJsonArray("levelMeans").asScala.map(_.getAsDouble).toList,
      nauseaThreshold = discomfort.get("nausea").getAsDouble,
      refusalThreshold = discomfort.get("refusal").getAsDouble,
      vomitChanceThreshold = discomfort.get("vomitChanceThreshold").getAsDouble,
      vomitMinChancePerTick = discomfort.get("vomitMinChancePerTick").getAsDouble,
      vomitMaxChancePerTick = discomfort.get("vomitMaxChancePerTick").getAsDouble,
      vomitRelief = discomfort.get("vomitRelief").getAsDouble,
      vomitReliefSpreadFraction = discomfort.get("vomitReliefSpreadFraction").getAsDouble,
      discomfortOverrides = discomfortOverrides,
      wounds = wounds
    )
  }
}
