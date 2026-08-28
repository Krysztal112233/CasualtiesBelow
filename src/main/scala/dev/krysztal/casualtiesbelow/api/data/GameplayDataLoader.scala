package dev.krysztal.casualtiesbelow.api.data

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.Using

import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import net.minecraft.core.HolderLookup
import net.minecraft.resources.FileToIdConverter
import net.minecraft.resources.Identifier
import net.minecraft.resources.RegistryOps
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.StrictJsonParser
import net.minecraft.util.profiling.ProfilerFiller

import dev.krysztal.casualtiesbelow.CasualtiesBelow

import com.google.gson.JsonElement

/** Reloads one keyed gameplay-data directory. Pack stacking selects the resource for each id before
  * preparation; malformed files are skipped independently, and apply publishes one immutable map.
  */
final class GameplayDataLoader[T](
    directorySegment: String,
    codec: Codec[T]
) extends SimplePreparableReloadListener[Map[Identifier, JsonElement]] {
  private val converter =
    FileToIdConverter.json(s"${CasualtiesBelow.ModId}/$directorySegment")

  @volatile private var registryLookup: Option[HolderLookup.Provider] = None
  @volatile private var entries: Map[Identifier, T] = Map.empty

  /** Immutable snapshot of the current entries keyed by datapack id. Ordering by priority happens
    * at lookup time (see `GameplayDataLookup.orderedEntries`).
    */
  def byId: Map[Identifier, T] = entries

  /** Resolves one entry without throwing when a datapack removed or malformed it. */
  def entry(id: Identifier): Option[T] = entries.get(id)

  private[data] def bind(lookup: HolderLookup.Provider): GameplayDataLoader[T] = {
    registryLookup = Some(lookup)
    this
  }

  override protected def prepare(
      manager: ResourceManager,
      profiler: ProfilerFiller
  ): Map[Identifier, JsonElement] = {
    val prepared = Map.newBuilder[Identifier, JsonElement]

    converter.listMatchingResources(manager).asScala.foreach { (location, resource) =>
      val id = converter.fileToId(location)
      Using(resource.openAsReader())(StrictJsonParser.parse(_)) match {
        case Success(json)  => prepared += id -> json
        case Failure(error) =>
          CasualtiesBelow.Logger.warn(
            s"Couldn't read gameplay data file '$id' from '$location'",
            error
          )
      }
    }

    prepared.result()
  }

  override protected def apply(
      prepared: Map[Identifier, JsonElement],
      manager: ResourceManager,
      profiler: ProfilerFiller
  ): Unit = {
    val decoded = Map.newBuilder[Identifier, T]
    val lookup = registryLookup.getOrElse {
      throw IllegalStateException(s"No registry lookup bound for gameplay data '$directorySegment'")
    }
    val ops = RegistryOps.create(JsonOps.INSTANCE, lookup)

    prepared.foreach { (id, json) =>
      Try(codec.parse(ops, json)) match {
        case Failure(error) =>
          CasualtiesBelow.Logger.warn(
            s"Couldn't decode gameplay data entry '$id' from directory '$directorySegment'",
            error
          )
        case Success(result) =>
          result
            .result()
            .toScala
            .fold {
              val reason = result.error().toScala.map(_.message()).getOrElse("unknown decode error")
              CasualtiesBelow.Logger.warn(
                "Couldn't decode gameplay data entry '{}' from directory '{}': {}",
                id,
                directorySegment,
                reason
              )
            } { entry =>
              decoded += id -> entry
            }
      }
    }

    entries = decoded.result()
  }
}
