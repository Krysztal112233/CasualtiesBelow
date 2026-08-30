package dev.krysztal.casualtiesbelow.internal.data

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.util.Try

import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import net.minecraft.core.HolderLookup
import net.minecraft.resources.Identifier
import net.minecraft.resources.RegistryOps
import net.minecraft.server.MinecraftServer

import net.fabricmc.fabric.api.resource.v1.DataResourceStore

import dev.krysztal.casualtiesbelow.data.schema.AdrenalineRuleData
import dev.krysztal.casualtiesbelow.data.schema.ArmorProtectionData
import dev.krysztal.casualtiesbelow.data.schema.DiscomfortData
import dev.krysztal.casualtiesbelow.data.schema.HitLocationData
import dev.krysztal.casualtiesbelow.data.schema.WoundProfile
import dev.krysztal.casualtiesbelow.data.schema.WoundRuleData

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** One immutable view of all datapack-defined gameplay data types. */
final case class GameplayDataStore(
    woundProfiles: Map[Identifier, WoundProfile],
    woundRules: Map[Identifier, WoundRuleData],
    armorProtection: Map[Identifier, ArmorProtectionData],
    discomfort: Map[Identifier, DiscomfortData],
    hitLocations: Map[Identifier, HitLocationData],
    adrenalineRules: Map[Identifier, AdrenalineRuleData]
)

object GameplayDataStore {

  val Empty: GameplayDataStore = GameplayDataStore(
    woundProfiles = Map.empty,
    woundRules = Map.empty,
    armorProtection = Map.empty,
    discomfort = Map.empty,
    hitLocations = Map.empty,
    adrenalineRules = Map.empty
  )
}

/** One resource-generation-owned view, including derived data compiled from its raw maps. */
private[casualtiesbelow] final class GameplayDataState private[internal] (
    val store: GameplayDataStore,
    val woundRules: CompiledWoundRules
)

private[internal] object GameplayDataState {
  def compile(store: GameplayDataStore): GameplayDataState = {
    new GameplayDataState(store, WoundProfiles.compile(store))
  }
}

/** Selects the current server resource generation. Client data is published atomically as part of
  * `GameplayDataSnapshot`, never as a second independently visible store.
  */
object GameplayDataStores {
  private[internal] val StateKey = new DataResourceStore.Key[GameplayDataState]()

  /** Resolves the data attached to the server's currently installed resource generation. */
  def server(server: MinecraftServer): GameplayDataStore = state(server).store

  private[casualtiesbelow] def state(server: MinecraftServer): GameplayDataState = {
    server.getOrThrow(StateKey)
  }

  private[internal] def publish(
      target: DataResourceStore.Mutable,
      state: GameplayDataState
  ): Unit = target.put(StateKey, state)

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

  /** Decodes a complete current-generation payload. Any invalid entry rejects the entire payload;
    * the client retains its prior complete snapshot and can retry the raw payload after tags load.
    */
  private[casualtiesbelow] def decode(
      root: JsonObject,
      lookup: HolderLookup.Provider
  ): GameplayDataStore = {
    val ops = RegistryOps.create(JsonOps.INSTANCE, lookup)
    GameplayDataStore(
      woundProfiles = decodeSection(root, "wound_profile", WoundProfile.Codec, ops),
      woundRules = decodeSection(root, "wound_rule", WoundRuleData.Codec, ops),
      armorProtection = decodeSection(root, "armor_protection", ArmorProtectionData.Codec, ops),
      discomfort = decodeSection(root, "discomfort", DiscomfortData.Codec, ops),
      hitLocations = decodeSection(root, "hit_location", HitLocationData.Codec, ops),
      adrenalineRules = decodeSection(root, "adrenaline_rule", AdrenalineRuleData.Codec, ops)
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
      root: JsonObject,
      name: String,
      codec: Codec[T],
      ops: RegistryOps[JsonElement]
  ): Map[Identifier, T] = {
    Option(root.get(name))
      .filter(_.isJsonObject)
      .map(_.getAsJsonObject)
      .getOrElse(throw IllegalArgumentException(s"Missing gameplay data section '$name'"))
      .entrySet()
      .asScala
      .map { entry =>
        val id = Try(Identifier.parse(entry.getKey)).getOrElse {
          throw IllegalArgumentException(
            s"Invalid gameplay data id '${entry.getKey}' in section '$name'"
          )
        }
        val result = codec.parse(ops, entry.getValue)
        val decoded = result.result().toScala.getOrElse {
          val reason = result.error().toScala.map(_.message()).getOrElse("codec rejected it")
          throw IllegalArgumentException(
            s"Couldn't decode gameplay data entry '$id' in section '$name': $reason"
          )
        }
        id -> decoded
      }
      .toMap
  }
}
