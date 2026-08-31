package dev.krysztal.casualtiesbelow.immune

import scala.jdk.OptionConverters.*
import scala.jdk.StreamConverters.*

import net.minecraft.core.Holder
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.RandomSource
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

import dev.krysztal.casualtiesbelow.component.ComponentAccess
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.data.schema.FoodImmuneData
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataStore
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataStores

/** Food immune settlement: nourishing food grants a one-off immune dose and contaminated food
  * drains it. Values are path-keyed datapack data: `food_immune/item/<ns>/<path>.json` prices item
  * `<ns>:<path>` exactly, `food_immune/tag/<ns>/<path>.json` prices every item carrying item tag
  * `<ns>:<path>`. Foods with no matching entry contribute nothing.
  *
  * This is a pulse on consumption, complementing the slow fed-state trickle in injury progression:
  * the pulse prices *what* you eat, the trickle prices *whether* you ate. One bite of properly
  * cooked food roughly offsets one zombie hit; contaminated food costs less than that hit. Vanilla
  * already punishes poison-bearing food through the Poison effect's continuous drain, so such food
  * carries no instant drain by default.
  *
  * The dose floats (gaussian around the entry mean, see
  * [[CasualtiesBelowConfig.FoodImmuneSpreadFraction]]) with the mean's sign preserved: jitter never
  * turns a nourishing food harmful or vice versa.
  */
object FoodImmunity {

  /** Applies the immune effect of a just-consumed food item. Called by the `Consumable` mixin on
    * the server when a player finishes eating or drinking something with a food component.
    */
  def onFoodEaten(player: ServerPlayer, stack: ItemStack): Unit = {
    val store = GameplayDataStores.server(player.level().getServer)
    resolve(stack.typeHolder(), store).foreach { mean =>
      if (mean != 0.0) {
        val vitals = ComponentAccess.vitals(player)
        val amount =
          sample(mean, CasualtiesBelowConfig.FoodImmuneSpreadFraction.get(), player.getRandom)
        val next = (vitals.infection.immuneHealth + amount)
          .max(0.0)
          .min(CasualtiesBelowConfig.MaxImmuneHealth.get())
        if (next != vitals.infection.immuneHealth) {
          VitalsMutations.setImmuneHealth(vitals, next)
          VitalsMutations.syncNow(player)
        }
      }
    }
  }

  /** Resolves the immune mean for [item]: the exact per-item entry wins; otherwise the value of the
    * carried tag with the largest absolute value (ties favor the nourishing value).
    */
  private[casualtiesbelow] def resolve(
      item: Holder[Item],
      store: GameplayDataStore
  ): Option[Double] = {
    item.unwrapKey().toScala.flatMap { key =>
      resolveValue(
        key.identifier(),
        item.tags().toScala(Set).map(_.location),
        store.foodImmuneItems,
        store.foodImmuneTags
      )
    }
  }

  /** Pure resolution: exact item entry first; among carried tags, the largest absolute value wins
    * and an exact tie favors the positive (nourishing) value — deterministic either way.
    */
  private[casualtiesbelow] def resolveValue(
      itemId: Identifier,
      itemTags: Set[Identifier],
      items: Map[Identifier, FoodImmuneData],
      tags: Map[Identifier, FoodImmuneData]
  ): Option[Double] = {
    items.get(itemId).map(_.immune).orElse {
      val matches = itemTags.flatMap(tags.get).map(_.immune)
      if (matches.isEmpty) None
      else Some(matches.maxBy(value => (math.abs(value), value)))
    }
  }

  /** Samples one dose around [mean] (gaussian, spread = |mean| × fraction). The sign of the mean is
    * preserved: jitter never turns a nourishing food harmful or a contaminated one beneficial.
    */
  private[casualtiesbelow] def sample(
      mean: Double,
      spreadFraction: Double,
      random: RandomSource
  ): Double = {
    val sampled = mean + random.nextGaussian() * math.abs(mean) * spreadFraction
    if (mean > 0.0) sampled.max(0.0) else sampled.min(0.0)
  }
}
