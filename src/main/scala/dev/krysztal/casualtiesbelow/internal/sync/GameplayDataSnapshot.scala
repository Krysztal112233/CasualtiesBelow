package dev.krysztal.casualtiesbelow.internal.sync

import scala.jdk.CollectionConverters.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import net.minecraft.core.HolderLookup
import net.minecraft.server.MinecraftServer

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataStore
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataStores

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
    consciousnessKnockoutThreshold: Double,
    unconsciousWakeThreshold: Double,
    shockCollapseThreshold: Double,
    terminalHypoxiaDurationTicks: Int,
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
    doseSpreadFraction: Double,
    gameplayData: GameplayDataStore
) {

  def toJson(lookup: HolderLookup.Provider): String = {
    jsonObject(
      "schemaVersion" -> Some(JsonPrimitive(GameplayDataSnapshot.CurrentSchemaVersion)),
      "vitals" -> Some(
        jsonObject(
          "maxBloodVolume" -> Some(JsonPrimitive(maxBloodVolume)),
          "bloodOxygenHypoxiaThreshold" -> Some(JsonPrimitive(bloodOxygenHypoxiaThreshold)),
          "consciousnessKnockoutThreshold" -> Some(
            JsonPrimitive(consciousnessKnockoutThreshold)
          ),
          "unconsciousWakeThreshold" -> Some(JsonPrimitive(unconsciousWakeThreshold)),
          "shockCollapseThreshold" -> Some(JsonPrimitive(shockCollapseThreshold)),
          "terminalHypoxiaDurationTicks" -> Some(JsonPrimitive(terminalHypoxiaDurationTicks))
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
          "doseSpreadFraction" -> Some(JsonPrimitive(doseSpreadFraction))
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

  private val CurrentSchemaVersion = 7

  private final case class ClientState(
      rawJson: Option[String],
      snapshot: Option[GameplayDataSnapshot]
  )

  private val EmptyClientState = ClientState(None, None)

  /** The raw payload and decoded snapshot are published together so readers can never observe
    * values belonging to different server resource generations.
    */
  @volatile private var clientState: ClientState = EmptyClientState

  /** What client-side displays and prediction should read: the server's snapshot when connected,
    * else the local configuration. Server logic must NOT read this client state (singleplayer
    * shares the JVM), so the server always captures from its installed resource generation instead.
    */
  def current: GameplayDataSnapshot = {
    clientState.snapshot.getOrElse(capture(GameplayDataStore.Empty))
  }

  /** Decodes and stores a snapshot received from the server. Malformed payloads are logged and
    * ignored, keeping the previous config and per-object data together. The raw payload is kept
    * either way so [[rereceive]] can retry it against fresher tag contents.
    */
  def receive(json: String, lookup: HolderLookup.Provider): Unit = this.synchronized {
    val previous = clientState
    Try(fromJson(json, lookup)) match {
      case Success(snapshot) => clientState = ClientState(Some(json), Some(snapshot))
      case Failure(error)    =>
        clientState = previous.copy(rawJson = Some(json))
        CasualtiesBelow.Logger.warn(
          "Ignoring malformed gameplay data sync; retaining the previous complete snapshot: {}",
          error.getMessage
        )
    }
  }

  /** Re-decodes the latest received payload against the given (fresh) registry access. On a
    * dedicated-server `/reload`, the vanilla tag packet is processed (and `TAGS_LOADED` fires)
    * after this payload arrives: a payload referencing tag keys added by that reload is rejected as
    * a whole on the first pass and can decode here before the following recipe packet triggers
    * JEI's rebuild. No-op when nothing was received (e.g. before join or after disconnect).
    */
  def rereceive(lookup: HolderLookup.Provider): Unit = {
    clientState.rawJson.foreach(receive(_, lookup))
  }

  def clearSynced(): Unit = this.synchronized {
    clientState = EmptyClientState
  }

  /** Builds the authoritative snapshot from the server's installed resource generation. */
  def capture(server: MinecraftServer): GameplayDataSnapshot = {
    capture(GameplayDataStores.server(server))
  }

  /** Builds a config snapshot around an already captured gameplay-data generation. */
  private[casualtiesbelow] def capture(
      gameplayData: GameplayDataStore
  ): GameplayDataSnapshot = {
    val config = CasualtiesBelowConfig
    GameplayDataSnapshot(
      maxBloodVolume = Consts.Vitals.MaxBloodVolume,
      bloodOxygenHypoxiaThreshold = Consts.Vitals.BloodOxygenHypoxiaThreshold,
      consciousnessKnockoutThreshold = Consts.Vitals.ConsciousnessKnockoutThreshold,
      unconsciousWakeThreshold = Consts.Vitals.ConsciousnessWakeThreshold,
      shockCollapseThreshold = Consts.Pain.ShockCollapseThreshold,
      terminalHypoxiaDurationTicks = Consts.Hazards.TerminalHypoxiaDurationTicks.max(1),
      armorSkinFormula = Consts.Armor.ArmorSkinFactorFormula.source,
      armorMuscleFormula = Consts.Armor.ArmorMuscleFactorFormula.source,
      maxDiscomfort = Consts.Discomfort.MaxValue,
      discomfortLevelMeans = List(
        Consts.Discomfort.Level1Mean,
        Consts.Discomfort.Level2Mean,
        Consts.Discomfort.Level3Mean
      ),
      nauseaThreshold = Consts.Discomfort.NauseaThreshold,
      refusalThreshold = config.medicineFood.refusalThreshold.get(),
      vomitChanceThreshold = Consts.Discomfort.VomitChanceThreshold,
      vomitMinChancePerTick = Consts.Discomfort.VomitMinChancePerTick,
      vomitMaxChancePerTick = math.max(
        Consts.Discomfort.VomitMaxChancePerTick,
        Consts.Discomfort.VomitMinChancePerTick
      ),
      vomitRelief = Consts.Discomfort.VomitRelief,
      doseSpreadFraction = Consts.Randomness.DoseSpreadFraction,
      gameplayData = gameplayData
    )
  }

  /** Decodes only the current complete snapshot shape. The mod has no released wire contract yet,
    * so combining an incomplete payload with local values would only conceal a broken generation.
    */
  private def fromJson(
      json: String,
      lookup: HolderLookup.Provider
  ): GameplayDataSnapshot = {
    val root = JsonParser.parseString(json).getAsJsonObject
    val schemaVersion = required(root, "schemaVersion").getAsInt
    if (schemaVersion != CurrentSchemaVersion) {
      throw IllegalArgumentException(
        s"Unsupported gameplay data schema version $schemaVersion (current: $CurrentSchemaVersion)"
      )
    }

    def section(name: String): JsonObject = {
      val value = required(root, name)
      if (!value.isJsonObject) {
        throw IllegalArgumentException(s"Gameplay data field '$name' must be an object")
      }
      value.getAsJsonObject
    }

    def requiredDouble(section: JsonObject, key: String): Double = {
      required(section, key).getAsDouble
    }

    def requiredString(section: JsonObject, key: String): String = {
      required(section, key).getAsString
    }

    def requiredInt(section: JsonObject, key: String): Int = {
      required(section, key).getAsInt
    }

    def requiredDoubles(section: JsonObject, key: String): List[Double] = {
      val value = required(section, key)
      if (!value.isJsonArray) {
        throw IllegalArgumentException(s"Gameplay data field '$key' must be an array")
      }
      val values = value.getAsJsonArray.asScala.map(_.getAsDouble).toList
      if (values.isEmpty) {
        throw IllegalArgumentException(s"Gameplay data field '$key' must not be empty")
      }
      values
    }

    val vitals = section("vitals")
    val armor = section("armor")
    val discomfort = section("discomfort")
    val gameplayData = GameplayDataStores.decode(section("data"), lookup)

    GameplayDataSnapshot(
      maxBloodVolume = requiredDouble(vitals, "maxBloodVolume"),
      bloodOxygenHypoxiaThreshold = requiredDouble(vitals, "bloodOxygenHypoxiaThreshold"),
      consciousnessKnockoutThreshold = requiredDouble(
        vitals,
        "consciousnessKnockoutThreshold"
      ),
      unconsciousWakeThreshold = requiredDouble(vitals, "unconsciousWakeThreshold"),
      shockCollapseThreshold = requiredDouble(vitals, "shockCollapseThreshold"),
      terminalHypoxiaDurationTicks = requiredInt(vitals, "terminalHypoxiaDurationTicks").max(1),
      armorSkinFormula = requiredString(armor, "skinFormula"),
      armorMuscleFormula = requiredString(armor, "muscleFormula"),
      maxDiscomfort = requiredDouble(discomfort, "maxValue"),
      discomfortLevelMeans = requiredDoubles(discomfort, "levelMeans"),
      nauseaThreshold = requiredDouble(discomfort, "nausea"),
      refusalThreshold = requiredDouble(discomfort, "refusal"),
      vomitChanceThreshold = requiredDouble(discomfort, "vomitChanceThreshold"),
      vomitMinChancePerTick = requiredDouble(discomfort, "vomitMinChancePerTick"),
      vomitMaxChancePerTick = requiredDouble(discomfort, "vomitMaxChancePerTick"),
      vomitRelief = requiredDouble(discomfort, "vomitRelief"),
      doseSpreadFraction = requiredDouble(discomfort, "doseSpreadFraction"),
      gameplayData = gameplayData
    )
  }

  private def required(objectValue: JsonObject, key: String): JsonElement = {
    Option(objectValue.get(key)).getOrElse {
      throw IllegalArgumentException(s"Missing gameplay data field '$key'")
    }
  }
}
