package dev.krysztal.casualtiesbelow.sync

import scala.jdk.CollectionConverters.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig.WoundProfileConfig
import dev.krysztal.casualtiesbelow.damage.ArmorProtectionOverrides
import dev.krysztal.casualtiesbelow.discomfort.DiscomfortOverrides

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** An armor protection override as shared with clients: the formula SOURCE strings (clients
  * recompile them with EvalEx; compiled expressions are not transferable).
  */
final case class ArmorOverrideData(
    items: List[String],
    tag: Option[String],
    skin: Option[String],
    muscle: Option[String]
)

/** A discomfort override as shared with clients (level XOR mean, as in the datapack files). */
final case class DiscomfortOverrideData(
    items: List[String],
    tag: Option[String],
    level: Option[Int],
    mean: Option[Double]
)

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
    discomfortLevelMeans: List[Double],
    nauseaThreshold: Double,
    refusalThreshold: Double,
    vomitThreshold: Double,
    discomfortOverrides: List[DiscomfortOverrideData],
    /** Wound profile coefficients by profile name: (skin, muscle, bleed, pain) per damage point. */
    wounds: Map[String, (Double, Double, Double, Double)]
) {

  def toJson: String = {
    val root = new JsonObject

    val armor = new JsonObject
    armor.addProperty("skinFormula", armorSkinFormula)
    armor.addProperty("muscleFormula", armorMuscleFormula)
    val armorArray = new JsonArray
    armorOverrides.foreach { o =>
      val e = new JsonObject
      if (o.items.nonEmpty) {
        val items = new JsonArray
        o.items.foreach(items.add)
        e.add("items", items)
      }
      o.tag.foreach(e.addProperty("tag", _))
      o.skin.foreach(e.addProperty("skin", _))
      o.muscle.foreach(e.addProperty("muscle", _))
      armorArray.add(e)
    }
    armor.add("overrides", armorArray)
    root.add("armor", armor)

    val discomfort = new JsonObject
    val means = new JsonArray
    discomfortLevelMeans.foreach(means.add(_))
    discomfort.add("levelMeans", means)
    discomfort.addProperty("nausea", nauseaThreshold)
    discomfort.addProperty("refusal", refusalThreshold)
    discomfort.addProperty("vomit", vomitThreshold)
    val discomfortArray = new JsonArray
    discomfortOverrides.foreach { o =>
      val e = new JsonObject
      if (o.items.nonEmpty) {
        val items = new JsonArray
        o.items.foreach(items.add)
        e.add("items", items)
      }
      o.tag.foreach(e.addProperty("tag", _))
      o.level.foreach(l => e.addProperty("level", l))
      o.mean.foreach(m => e.addProperty("mean", m))
      discomfortArray.add(e)
    }
    discomfort.add("overrides", discomfortArray)
    root.add("discomfort", discomfort)

    val woundObject = new JsonObject
    wounds.foreach { (name, p) =>
      val e = new JsonArray
      e.add(p._1)
      e.add(p._2)
      e.add(p._3)
      e.add(p._4)
      woundObject.add(name, e)
    }
    root.add("wounds", woundObject)

    root.toString
  }
}

object GameplayDataSnapshot {

  /** The latest snapshot received from the server, if any. Cleared on disconnect. */
  @volatile private var synced: Option[GameplayDataSnapshot] = None

  /** What displays and client prediction should read: the server's snapshot when connected, else
    * the local configuration and locally visible (builtin) overrides.
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
      discomfortLevelMeans = List(
        config.DiscomfortLevel1Mean.get(),
        config.DiscomfortLevel2Mean.get(),
        config.DiscomfortLevel3Mean.get()
      ),
      nauseaThreshold = config.DiscomfortNauseaThreshold.get(),
      refusalThreshold = config.DiscomfortRefusalThreshold.get(),
      vomitThreshold = config.DiscomfortVomitThreshold.get(),
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
      discomfortLevelMeans =
        discomfort.getAsJsonArray("levelMeans").asScala.map(_.getAsDouble).toList,
      nauseaThreshold = discomfort.get("nausea").getAsDouble,
      refusalThreshold = discomfort.get("refusal").getAsDouble,
      vomitThreshold = discomfort.get("vomit").getAsDouble,
      discomfortOverrides = discomfortOverrides,
      wounds = wounds
    )
  }
}
