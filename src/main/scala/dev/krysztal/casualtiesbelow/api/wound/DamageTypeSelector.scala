package dev.krysztal.casualtiesbelow.api.wound

import scala.jdk.CollectionConverters.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.TagKey
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageType

/** A lazy damage-type selector entry. Unlike a registry-backed HolderSet, the key need not exist
  * while the datapack is decoded; absent optional compatibility mods therefore simply never match.
  */
sealed trait DamageTypeSelectorEntry {
  def matches(source: DamageSource): Boolean
  def serialized: String
}

final case class ExactDamageType(key: ResourceKey[DamageType]) extends DamageTypeSelectorEntry {
  override def matches(source: DamageSource): Boolean = source.is(key)
  override def serialized: String = key.identifier().toString
}

final case class TaggedDamageType(tag: TagKey[DamageType]) extends DamageTypeSelectorEntry {
  override def matches(source: DamageSource): Boolean = source.is(tag)
  override def serialized: String = s"#${tag.location()}"
}

object DamageTypeSelectorEntry {
  val Codec: Codec[DamageTypeSelectorEntry] = com.mojang.serialization.Codec.STRING.comapFlatMap(
    parse,
    _.serialized
  )

  private def parse(raw: String): DataResult[DamageTypeSelectorEntry] = {
    val (tagged, value) =
      if (raw.startsWith("#")) (true, raw.drop(1))
      else (false, raw)
    Try(Identifier.parse(value)) match {
      case Success(id) if tagged =>
        DataResult.success(TaggedDamageType(TagKey.create(Registries.DAMAGE_TYPE, id)))
      case Success(id) =>
        DataResult.success(
          ExactDamageType(ResourceKey.create(Registries.DAMAGE_TYPE, id))
        )
      case Failure(error) =>
        DataResult.error(() => s"Invalid damage type selector '$raw': ${error.getMessage}")
    }
  }
}

/** Any-of selector accepting a single exact ID/tag or a mixed list of both forms. */
final case class DamageTypeSelector(entries: List[DamageTypeSelectorEntry]) {
  def matches(source: DamageSource): Boolean = entries.exists(_.matches(source))
}

object DamageTypeSelector {
  private val ListCodec: Codec[List[DamageTypeSelectorEntry]] = DamageTypeSelectorEntry.Codec
    .listOf()
    .xmap(_.asScala.toList, _.asJava)

  val Codec: Codec[DamageTypeSelector] = com.mojang.serialization.Codec
    .either(DamageTypeSelectorEntry.Codec, ListCodec)
    .xmap(
      _.map(entry => DamageTypeSelector(List(entry)), DamageTypeSelector.apply),
      selector =>
        selector.entries match {
          case entry :: Nil => Either.left(entry)
          case entries      => Either.right(entries)
        }
    )
    .validate(selector =>
      if (selector.entries.nonEmpty) DataResult.success(selector)
      else DataResult.error(() => "A damage type selector list cannot be empty")
    )
}
