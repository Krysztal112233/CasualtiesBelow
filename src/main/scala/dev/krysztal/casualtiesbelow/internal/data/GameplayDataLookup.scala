package dev.krysztal.casualtiesbelow.internal.data

import scala.jdk.OptionConverters.*

import net.minecraft.core.Holder
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

import dev.krysztal.casualtiesbelow.data.schema.ArmorProtectionData
import dev.krysztal.casualtiesbelow.data.schema.FoodEffectsData
import dev.krysztal.casualtiesbelow.data.schema.HitLocationData

/** Deterministic lookups shared by gameplay consumers of the reload-listener stores. */
object GameplayDataLookup {

  /** Entries ordered by descending priority, then ascending datapack id. */
  def orderedEntries[T](
      entries: Map[Identifier, T]
  )(priority: T => Int): List[(Identifier, T)] = {
    entries.toList.sortBy { (id, value) => (-priority(value).toLong, id.toString) }
  }

  /** Resolves one entry without throwing when a datapack removed it. */
  def entry[T](entries: Map[Identifier, T], id: Identifier): Option[T] = entries.get(id)

  def hitLocation(
      entity: Entity,
      store: GameplayDataStore
  ): HitLocationData = {
    orderedEntries(store.hitLocations)(_.priority.intValue())
      .collectFirst {
        case (_, rules) if rules.entities.contains(entityTypeHolder(entity)) => rules
      }
      .getOrElse(HitLocationData.Fallback)
  }

  def armorProtection(
      stack: ItemStack,
      store: GameplayDataStore
  ): Option[ArmorProtectionData] = {
    orderedEntries(store.armorProtection)(_.priority.intValue())
      .collectFirst { case (_, entry) if entry.items.contains(stack.typeHolder()) => entry }
  }

  /** The exact per-item food effects entry, if the item has one. Tag-level fallback for immune
    * values is resolved by the consumer (`immune.FoodImmunity`).
    */
  def foodEffects(item: Holder[Item], store: GameplayDataStore): Option[FoodEffectsData] = {
    item.unwrapKey().toScala.flatMap(key => store.foodEffects.get(key.identifier()))
  }

  private def entityTypeHolder(entity: Entity) = entity.typeHolder()
}
