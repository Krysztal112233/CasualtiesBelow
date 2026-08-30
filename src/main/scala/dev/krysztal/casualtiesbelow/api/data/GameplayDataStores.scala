package dev.krysztal.casualtiesbelow.api.data

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import net.minecraft.core.HolderLookup
import net.minecraft.resources.Identifier
import net.minecraft.resources.RegistryOps
import net.minecraft.server.MinecraftServer

import net.fabricmc.fabric.api.resource.v1.DataResourceStore

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.wound.CompiledWoundRules
import dev.krysztal.casualtiesbelow.api.wound.WoundProfile
import dev.krysztal.casualtiesbelow.api.wound.WoundProfiles

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** One immutable view of all datapack-defined gameplay data types. */
final case class GameplayDataStore(
    woundProfiles: Map[Identifier, WoundProfile],
    woundRules: Map[Identifier, WoundRuleData],
    armorProtection: Map[Identifier, ArmorProtectionData],
    discomfort: Map[Identifier, DiscomfortData],
    hitLocations: Map[Identifier, HitLocationData],
    adrenalineRules: Map[Identifier, AdrenalineRuleData] = Map.empty
) {

  /** Binary bridge for integrations compiled against the five-section v2 store. New code should use
    * the generated six-argument constructor or companion `apply`.
    */
  @deprecated("Pass adrenalineRules explicitly", "1.0.0")
  def this(
      woundProfiles: Map[Identifier, WoundProfile],
      woundRules: Map[Identifier, WoundRuleData],
      armorProtection: Map[Identifier, ArmorProtectionData],
      discomfort: Map[Identifier, DiscomfortData],
      hitLocations: Map[Identifier, HitLocationData]
  ) = this(woundProfiles, woundRules, armorProtection, discomfort, hitLocations, Map.empty)

  /** Retains the old five-argument `copy` descriptor and preserves this store's adrenaline rules.
    */
  @deprecated("Pass adrenalineRules explicitly", "1.0.0")
  def copy(
      woundProfiles: Map[Identifier, WoundProfile],
      woundRules: Map[Identifier, WoundRuleData],
      armorProtection: Map[Identifier, ArmorProtectionData],
      discomfort: Map[Identifier, DiscomfortData],
      hitLocations: Map[Identifier, HitLocationData]
  ): GameplayDataStore =
    GameplayDataStore(
      woundProfiles,
      woundRules,
      armorProtection,
      discomfort,
      hitLocations,
      adrenalineRules
    )
}

object GameplayDataStore {

  val Empty: GameplayDataStore = GameplayDataStore(
    woundProfiles = Map.empty,
    woundRules = Map.empty,
    armorProtection = Map.empty,
    discomfort = Map.empty,
    hitLocations = Map.empty,
    adrenalineRules = Map.empty
  )

  /** Source and binary bridge for the pre-adrenaline five-section store constructor. */
  @deprecated("Pass adrenalineRules explicitly", "1.0.0")
  def apply(
      woundProfiles: Map[Identifier, WoundProfile],
      woundRules: Map[Identifier, WoundRuleData],
      armorProtection: Map[Identifier, ArmorProtectionData],
      discomfort: Map[Identifier, DiscomfortData],
      hitLocations: Map[Identifier, HitLocationData]
  ): GameplayDataStore =
    new GameplayDataStore(
      woundProfiles,
      woundRules,
      armorProtection,
      discomfort,
      hitLocations,
      Map.empty
    )
}

/** One resource-generation-owned view, including derived data compiled from its raw maps. */
private[casualtiesbelow] final class GameplayDataState private[data] (
    val store: GameplayDataStore,
    val woundRules: CompiledWoundRules
)

private[data] object GameplayDataState {
  def compile(store: GameplayDataStore): GameplayDataState = {
    new GameplayDataState(store, WoundProfiles.compile(store))
  }
}

/** Selects the current server resource generation or the latest client-synced maps. Each server
  * generation owns a separate [[GameplayDataState]] through Fabric's [[DataResourceStore]].
  */
object GameplayDataStores {
  private[data] val StateKey = new DataResourceStore.Key[GameplayDataState]()

  @volatile private var synced: Option[GameplayDataStore] = None

  /** Resolves the data attached to the server's currently installed resource generation. */
  def server(server: MinecraftServer): GameplayDataStore = state(server).store

  /** Client view: the latest server payload, or no datapack entries before one arrives. */
  def client: GameplayDataStore = synced.getOrElse(GameplayDataStore.Empty)

  private[casualtiesbelow] def state(server: MinecraftServer): GameplayDataState = {
    server.getOrThrow(StateKey)
  }

  private[data] def publish(
      target: DataResourceStore.Mutable,
      state: GameplayDataState
  ): Unit = target.put(StateKey, state)

  private[casualtiesbelow] def setSynced(store: GameplayDataStore): Unit = synced = Some(store)

  private[casualtiesbelow] def clearSynced(): Unit = synced = None

  /** Encodes all maps with the same registry-aware codecs used by the reload listeners. */
  private[casualtiesbelow] def encode(
      store: GameplayDataStore,
      lookup: HolderLookup.Provider
  ): JsonObject = {
    val ops = RegistryOps.create(JsonOps.INSTANCE, lookup)
    val root = new JsonObject
    root.add("wound_profile", encodeSection(store.woundProfiles, WoundProfile.Codec, ops))
    root.add("wound_rule", encodeSection(store.woundRules, WoundRuleData.Codec, ops))
    root.add(
      "adrenaline_rule",
      encodeSection(store.adrenalineRules, AdrenalineRuleData.Codec, ops)
    )
    root.add(
      "armor_protection",
      encodeSection(store.armorProtection, ArmorProtectionData.Codec, ops)
    )
    root.add("discomfort", encodeSection(store.discomfort, DiscomfortData.Codec, ops))
    root.add("hit_location", encodeSection(store.hitLocations, HitLocationData.Codec, ops))
    root
  }

  /** Decodes whichever sections are present. Missing sections retain the supplied local fallback,
    * which keeps older payloads and singleplayer transition states usable. Within a section,
    * entries that fail to decode are logged and skipped individually (mirroring the server-side
    * loader), so one bad entry — e.g. referencing a tag key a `/reload` has not synced yet — never
    * discards the whole section.
    */
  private[casualtiesbelow] def decode(
      root: Option[JsonObject],
      lookup: HolderLookup.Provider,
      fallback: GameplayDataStore
  ): GameplayDataStore = {
    val ops = RegistryOps.create(JsonOps.INSTANCE, lookup)
    GameplayDataStore(
      woundProfiles = decodeSection(root, "wound_profile", WoundProfile.Codec, ops)
        .getOrElse(fallback.woundProfiles),
      woundRules = decodeSection(root, "wound_rule", WoundRuleData.Codec, ops)
        .getOrElse(fallback.woundRules),
      armorProtection = decodeSection(root, "armor_protection", ArmorProtectionData.Codec, ops)
        .getOrElse(fallback.armorProtection),
      discomfort = decodeSection(root, "discomfort", DiscomfortData.Codec, ops)
        .getOrElse(fallback.discomfort),
      hitLocations = decodeSection(root, "hit_location", HitLocationData.Codec, ops)
        .getOrElse(fallback.hitLocations),
      adrenalineRules = decodeSection(root, "adrenaline_rule", AdrenalineRuleData.Codec, ops)
        .getOrElse(fallback.adrenalineRules)
    )
  }

  private def encodeSection[T](
      entries: Map[Identifier, T],
      codec: Codec[T],
      ops: RegistryOps[JsonElement]
  ): JsonObject = {
    val section = new JsonObject
    entries.toList.sortBy(_._1.toString).foreach { (id, entry) =>
      val result = codec.encodeStart(ops, entry)
      val json = result.result().toScala.getOrElse {
        val reason = result.error().toScala.map(_.message()).getOrElse("unknown encode error")
        throw IllegalStateException(s"Couldn't encode gameplay data entry '$id': $reason")
      }
      section.add(id.toString, json)
    }
    section
  }

  private def decodeSection[T](
      root: Option[JsonObject],
      name: String,
      codec: Codec[T],
      ops: RegistryOps[JsonElement]
  ): Option[Map[Identifier, T]] = {
    root
      .flatMap(obj => Option(obj.get(name)))
      .filter(_.isJsonObject)
      .map { element =>
        element.getAsJsonObject
          .entrySet()
          .asScala
          .flatMap { entry =>
            Try {
              val id = Identifier.parse(entry.getKey)
              val result = codec.parse(ops, entry.getValue)
              result.result().toScala.map(id -> _)
            } match {
              case Success(Some(decoded)) => Some(decoded)
              case Success(None)          =>
                CasualtiesBelow.Logger.warn(
                  "Couldn't decode synced gameplay data entry '{}' from '{}': codec rejected it",
                  entry.getKey,
                  name
                )
                None
              case Failure(error) =>
                CasualtiesBelow.Logger.warn(
                  s"Couldn't decode synced gameplay data entry '${entry.getKey}' from '$name'",
                  error
                )
                None
            }
          }
          .toMap
      }
  }
}
