package dev.krysztal.casualtiesbelow.immune

import java.util.List as JList

import net.minecraft.SharedConstants
import net.minecraft.core.HolderSet
import net.minecraft.resources.Identifier
import net.minecraft.server.Bootstrap
import net.minecraft.util.RandomSource
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

import dev.krysztal.casualtiesbelow.data.schema.FoodImmuneData
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataStore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

final class FoodImmunityTest {

  SharedConstants.tryDetectVersion()
  Bootstrap.bootStrap()

  @Test
  def highestPriorityEntryWins(): Unit = {
    val store = storeOf(
      "broad" -> FoodImmuneData(itemsOf(Items.COOKED_BEEF), 1.0, 0),
      "specific" -> FoodImmuneData(itemsOf(Items.COOKED_BEEF), 3.0, 10)
    )
    assertEquals(
      3.0,
      FoodImmunity.resolve(Items.COOKED_BEEF.builtInRegistryHolder(), store).get,
      1.0e-9
    )
  }

  @Test
  def unlistedFoodResolvesNothing(): Unit = {
    val store = storeOf("soups" -> FoodImmuneData(itemsOf(Items.MUSHROOM_STEW), 4.0, 0))
    assertTrue(FoodImmunity.resolve(Items.COOKED_BEEF.builtInRegistryHolder(), store).isEmpty)
    assertTrue(
      FoodImmunity
        .resolve(Items.COOKED_BEEF.builtInRegistryHolder(), GameplayDataStore.Empty)
        .isEmpty
    )
  }

  @Test
  def explicitZeroMasksBroaderFallback(): Unit = {
    val store = storeOf(
      "all_food" -> FoodImmuneData(itemsOf(Items.POISONOUS_POTATO), -1.0, 0),
      "exemption" -> FoodImmuneData(itemsOf(Items.POISONOUS_POTATO), 0.0, 5)
    )
    assertEquals(
      0.0,
      FoodImmunity.resolve(Items.POISONOUS_POTATO.builtInRegistryHolder(), store).get,
      1.0e-9
    )
  }

  @Test
  def samplePreservesTheMeansSign(): Unit = {
    val random = RandomSource.create(42L)
    (1 to 2000).foreach { _ =>
      assertTrue(FoodImmunity.sample(2.0, 0.5, random) >= 0.0)
      assertTrue(FoodImmunity.sample(-3.0, 0.5, random) <= 0.0)
    }
  }

  @Test
  def zeroSpreadReturnsTheMeanAndZeroMeanStaysZero(): Unit = {
    val random = RandomSource.create(7L)
    (1 to 100).foreach { _ =>
      assertEquals(2.5, FoodImmunity.sample(2.5, 0.0, random), 1.0e-9)
      assertEquals(-1.5, FoodImmunity.sample(-1.5, 0.0, random), 1.0e-9)
      assertEquals(0.0, FoodImmunity.sample(0.0, 0.9, random), 1.0e-9)
    }
  }

  private def storeOf(entries: (String, FoodImmuneData)*): GameplayDataStore = {
    GameplayDataStore.Empty.copy(
      foodImmune = entries.map { (path, data) =>
        Identifier.fromNamespaceAndPath("test", path) -> data
      }.toMap
    )
  }

  private def itemsOf(first: Item, rest: Item*): HolderSet[Item] = {
    HolderSet.direct(JList.of((first +: rest).map(_.builtInRegistryHolder())*))
  }
}
