package dev.krysztal.casualtiesbelow.api.data

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack

/** Deterministic lookups shared by gameplay consumers of the synced dynamic registries. */
object GameplayDataLookup {

  /** Registry entries ordered by descending priority, then ascending registry id. */
  def orderedEntries[T](
      access: RegistryAccess,
      registryKey: ResourceKey[? <: Registry[T]]
  )(priority: T => Int): List[(ResourceKey[T], T)] = {
    access
      .lookup(registryKey)
      .toScala
      .toList
      .flatMap(_.listElements().iterator().asScala)
      .map(holder => holder.key() -> holder.value())
      .sortBy { (key, value) => (-priority(value).toLong, key.identifier().toString) }
  }

  /** Resolves one entry without throwing when a datapack removed it. */
  def entry[T](
      access: RegistryAccess,
      registryKey: ResourceKey[? <: Registry[T]],
      entryKey: ResourceKey[T]
  ): Option[T] = {
    access
      .lookup(registryKey)
      .toScala
      .flatMap(_.get(entryKey).toScala)
      .map(_.value())
  }

  def fallRules(access: RegistryAccess, entity: Entity): FallRulesData = {
    orderedEntries(access, CasualtiesBelowRegistries.FallRules)(_.priority.intValue())
      .collectFirst {
        case (_, rules) if rules.entities.contains(entityTypeHolder(entity)) => rules
      }
      .getOrElse(FallRulesData.Fallback)
  }

  def hitLocation(access: RegistryAccess, entity: Entity): HitLocationData = {
    orderedEntries(access, CasualtiesBelowRegistries.HitLocation)(_.priority.intValue())
      .collectFirst {
        case (_, rules) if rules.entities.contains(entityTypeHolder(entity)) => rules
      }
      .getOrElse(HitLocationData.Fallback)
  }

  def armorProtection(access: RegistryAccess, stack: ItemStack): Option[ArmorProtectionData] = {
    orderedEntries(access, CasualtiesBelowRegistries.ArmorProtection)(_.priority.intValue())
      .collectFirst { case (_, entry) if entry.items.contains(stack.typeHolder()) => entry }
  }

  def discomfort(access: RegistryAccess, stack: ItemStack): Option[DiscomfortData] = {
    orderedEntries(access, CasualtiesBelowRegistries.Discomfort)(_.priority.intValue())
      .collectFirst { case (_, entry) if entry.items.contains(stack.typeHolder()) => entry }
  }

  private def entityTypeHolder(entity: Entity) = entity.typeHolder()
}
