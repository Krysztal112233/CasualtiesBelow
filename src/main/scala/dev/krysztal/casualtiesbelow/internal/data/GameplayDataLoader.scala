package dev.krysztal.casualtiesbelow.internal.data

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
import net.minecraft.util.StrictJsonParser

import dev.krysztal.casualtiesbelow.CasualtiesBelow

import com.google.gson.JsonElement

/** Loads one keyed gameplay-data directory into an immutable map. Pack stacking selects the
  * resource for each id before decoding, and malformed files are skipped independently.
  */
private[internal] final class GameplayDataLoader[T](
    directorySegment: String,
    codec: Codec[T]
) {
  private val converter =
    FileToIdConverter.json(s"${CasualtiesBelow.ModId}/$directorySegment")

  private[internal] def load(
      manager: ResourceManager,
      lookup: HolderLookup.Provider
  ): Map[Identifier, T] = {
    val decoded = Map.newBuilder[Identifier, T]
    val ops = RegistryOps.create(JsonOps.INSTANCE, lookup)

    converter.listMatchingResources(manager).asScala.foreach { (location, resource) =>
      val id = converter.fileToId(location)
      Using(resource.openAsReader())(StrictJsonParser.parse(_)) match {
        case Success(json)  => decode(id, json, ops).foreach(entry => decoded += id -> entry)
        case Failure(error) =>
          CasualtiesBelow.Logger.warn(
            s"Couldn't read gameplay data file '$id' from '$location'",
            error
          )
      }
    }

    decoded.result()
  }

  private def decode(
      id: Identifier,
      json: JsonElement,
      ops: RegistryOps[JsonElement]
  ): Option[T] = {
    Try(codec.parse(ops, json)) match {
      case Failure(error) =>
        CasualtiesBelow.Logger.warn(
          s"Couldn't decode gameplay data entry '$id' from directory '$directorySegment'",
          error
        )
        None
      case Success(result) =>
        result.result().toScala.orElse {
          val reason = result.error().toScala.map(_.message()).getOrElse("unknown decode error")
          CasualtiesBelow.Logger.warn(
            "Couldn't decode gameplay data entry '{}' from directory '{}': {}",
            id,
            directorySegment,
            reason
          )
          None
        }
    }
  }
}
