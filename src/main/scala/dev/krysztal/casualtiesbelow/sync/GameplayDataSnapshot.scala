package dev.krysztal.casualtiesbelow.sync

import scala.jdk.CollectionConverters.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import net.minecraft.core.HolderLookup

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.data.GameplayDataStore
import dev.krysztal.casualtiesbelow.api.data.GameplayDataStores
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/** The server-effective config-derived gameplay numbers and datapack-defined data maps.
  *
  * The server is authoritative, but displays and client-side prediction need the same content. The
  * per-object maps are codec-encoded into this snapshot with a registry-aware context and decoded
  * against the current client level before publication.
  */
final case class GameplayDataSnapshot(
    maxBloodVolume: Double,
    bloodOxygenHypoxiaThreshold: Double,
    unconsciousWakeThreshold: Double,
    shockCollapseThreshold: Double,
    armorSkinFormula: String,
    armorMuscleFormula: String,
    maxDiscomfort: Double,
    discomfortLevelMeans: List[Double],
    nauseaThreshold: Double,
    refusalThreshold: Double,
    vomitChanceThreshold: Double,
    vomitMinChancePerTick: Double,
    vomitMaxChancePerTick: Double,
    vomitRelief: Double,
    vomitReliefSpreadFraction: Double,
    gameplayData: GameplayDataStore
) {

  def toJson(lookup: HolderLookup.Provider): String = {
    jsonObject(
      "schemaVersion" -> Some(JsonPrimitive(GameplayDataSnapshot.CurrentSchemaVersion)),
      "vitals" -> Some(
        jsonObject(
          "maxBloodVolume" -> Some(JsonPrimitive(maxBloodVolume)),
          "bloodOxygenHypoxiaThreshold" -> Some(JsonPrimitive(bloodOxygenHypoxiaThreshold)),
          "unconsciousWakeThreshold" -> Some(JsonPrimitive(unconsciousWakeThreshold)),
          "shockCollapseThreshold" -> Some(JsonPrimitive(shockCollapseThreshold))
        )
      ),
      "armor" -> Some(
        jsonObject(
          "skinFormula" -> Some(JsonPrimitive(armorSkinFormula)),
          "muscleFormula" -> Some(JsonPrimitive(armorMuscleFormula))
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
          "vomitReliefSpreadFraction" -> Some(JsonPrimitive(vomitReliefSpreadFraction))
        )
      ),
      "data" -> Some(GameplayDataStores.encode(gameplayData, lookup))
    ).toString
  }

  /** Builds a JSON array by mapping each element; the only place an array is mutated. */
  private def jsonArray[A](values: Iterable[A])(f: A => JsonElement): JsonArray = {
    val array = new JsonArray
    values.foreach(v => array.add(f(v)))
    array
  }

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

  private val CurrentSchemaVersion = 2

  /** The latest snapshot received from the server, if any. Cleared on disconnect. */
  @volatile private var synced: Option[GameplayDataSnapshot] = None

  /** The raw JSON of the latest received payload, kept so it can be re-decoded when tag contents
    * arrive after the payload (see [[rereceive]]). Cleared on disconnect.
    */
  @volatile private var lastReceivedJson: Option[String] = None

  /** What client-side displays and prediction should read: the server's snapshot when connected,
    * else the local configuration. Server logic must NOT read this: `synced` belongs to the logical
    * client (singleplayer shares the JVM), so the server always captures the live state via
    * [[capture]] instead.
    */
  def current: GameplayDataSnapshot = synced.getOrElse(capture())

  /** Decodes and stores a snapshot received from the server. Malformed payloads are logged and
    * ignored, keeping the previous config and per-object data together. The raw payload is kept
    * either way so [[rereceive]] can retry it against fresher tag contents.
    */
  def receive(json: String, lookup: HolderLookup.Provider): Unit = {
    lastReceivedJson = Some(json)
    Try(fromJson(json, lookup)) match {
      case Success(snapshot) =>
        GameplayDataStores.setSynced(snapshot.gameplayData)
        synced = Some(snapshot)
      case Failure(e) =>
        CasualtiesBelow.Logger.warn("Ignoring malformed gameplay data sync: {}", e.getMessage)
    }
  }

  /** Re-decodes the latest received payload against the given (fresh) registry access. On a
    * dedicated-server `/reload`, the vanilla tag packet is processed (and `TAGS_LOADED` fires)
    * after this payload arrives: entries referencing tag keys ADDED by that reload were skipped on
    * the first pass and decode on this one — before the following recipe packet triggers JEI's
    * rebuild. No-op when nothing was received (e.g. before join or after disconnect).
    */
  def rereceive(lookup: HolderLookup.Provider): Unit = {
    lastReceivedJson.foreach(receive(_, lookup))
  }

  def clearSynced(): Unit = {
    synced = None
    lastReceivedJson = None
    GameplayDataStores.clearSynced()
  }

  /** Builds the snapshot from the effective local config and the current reload-listener maps. Used
    * both by the server to fill the sync payload and by the client as fallback.
    */
  def capture(): GameplayDataSnapshot = {
    val config = CasualtiesBelowConfig
    GameplayDataSnapshot(
      maxBloodVolume = config.MaxBloodVolume.get(),
      bloodOxygenHypoxiaThreshold = config.BloodOxygenHypoxiaThreshold.get(),
      unconsciousWakeThreshold = config.ConsciousnessWakeThreshold.get(),
      shockCollapseThreshold = config.ShockCollapseThreshold.get(),
      armorSkinFormula = config.ArmorSkinFactorFormula.spec.get(),
      armorMuscleFormula = config.ArmorMuscleFactorFormula.spec.get(),
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
      gameplayData = GameplayDataStores.server
    )
  }

  /** Decodes both current and older snapshots. Missing sections or fields use local state so a
    * partial payload cannot break client prediction while connecting across a transition.
    */
  private def fromJson(
      json: String,
      lookup: HolderLookup.Provider
  ): GameplayDataSnapshot = {
    val fallback = capture()
    val root = JsonParser.parseString(json).getAsJsonObject
    val schemaVersion =
      Option(root.get("schemaVersion")).map(_.getAsInt).getOrElse(1)
    if (schemaVersion < 1 || schemaVersion > CurrentSchemaVersion) {
      throw IllegalArgumentException(
        s"Unsupported gameplay data schema version $schemaVersion (current: $CurrentSchemaVersion)"
      )
    }

    def section(name: String): Option[JsonObject] = {
      Option(root.get(name)).filter(_.isJsonObject).map(_.getAsJsonObject)
    }
    def optDouble(section: Option[JsonObject], key: String, default: => Double): Double = {
      section.flatMap(obj => Option(obj.get(key))).map(_.getAsDouble).getOrElse(default)
    }
    def optString(section: Option[JsonObject], key: String, default: => String): String = {
      section.flatMap(obj => Option(obj.get(key))).map(_.getAsString).getOrElse(default)
    }
    def optDoubles(
        section: Option[JsonObject],
        key: String,
        default: => List[Double]
    ): List[Double] = {
      section
        .flatMap(obj => Option(obj.getAsJsonArray(key)))
        .map(_.asScala.map(_.getAsDouble).toList)
        .filter(_.nonEmpty)
        .getOrElse(default)
    }

    val vitals = section("vitals")
    val armor = section("armor")
    val discomfort = section("discomfort")
    val gameplayData = GameplayDataStores.decode(section("data"), lookup, fallback.gameplayData)

    GameplayDataSnapshot(
      maxBloodVolume = optDouble(vitals, "maxBloodVolume", fallback.maxBloodVolume),
      bloodOxygenHypoxiaThreshold = optDouble(
        vitals,
        "bloodOxygenHypoxiaThreshold",
        fallback.bloodOxygenHypoxiaThreshold
      ),
      unconsciousWakeThreshold = optDouble(
        vitals,
        "unconsciousWakeThreshold",
        fallback.unconsciousWakeThreshold
      ),
      shockCollapseThreshold = optDouble(
        vitals,
        "shockCollapseThreshold",
        fallback.shockCollapseThreshold
      ),
      armorSkinFormula = optString(armor, "skinFormula", fallback.armorSkinFormula),
      armorMuscleFormula = optString(armor, "muscleFormula", fallback.armorMuscleFormula),
      maxDiscomfort = optDouble(discomfort, "maxValue", fallback.maxDiscomfort),
      discomfortLevelMeans = optDoubles(
        discomfort,
        "levelMeans",
        fallback.discomfortLevelMeans
      ),
      nauseaThreshold = optDouble(discomfort, "nausea", fallback.nauseaThreshold),
      refusalThreshold = optDouble(discomfort, "refusal", fallback.refusalThreshold),
      vomitChanceThreshold = optDouble(
        discomfort,
        "vomitChanceThreshold",
        fallback.vomitChanceThreshold
      ),
      vomitMinChancePerTick = optDouble(
        discomfort,
        "vomitMinChancePerTick",
        fallback.vomitMinChancePerTick
      ),
      vomitMaxChancePerTick = optDouble(
        discomfort,
        "vomitMaxChancePerTick",
        fallback.vomitMaxChancePerTick
      ),
      vomitRelief = optDouble(discomfort, "vomitRelief", fallback.vomitRelief),
      vomitReliefSpreadFraction = optDouble(
        discomfort,
        "vomitReliefSpreadFraction",
        fallback.vomitReliefSpreadFraction
      ),
      gameplayData = gameplayData
    )
  }
}
