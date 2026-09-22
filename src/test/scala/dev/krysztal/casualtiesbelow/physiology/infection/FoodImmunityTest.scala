package dev.krysztal.casualtiesbelow.physiology.infection

import net.minecraft.SharedConstants
import net.minecraft.resources.Identifier
import net.minecraft.server.Bootstrap
import net.minecraft.util.RandomSource
import net.minecraft.world.item.Items

import dev.krysztal.casualtiesbelow.data.schema.FoodEffectsData
import dev.krysztal.casualtiesbelow.data.schema.FoodImmuneData
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataStore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

final class FoodImmunityTest {

  SharedConstants.tryDetectVersion()
  Bootstrap.bootStrap()

  @Test
  def exactItemEntryWins(): Unit = {
    assertEquals(
      2.0,
      FoodImmunity
        .resolveValue(
          id("cooked_beef"),
          Set.empty,
          items = Map(id("cooked_beef") -> FoodEffectsData.immune(2.0)),
          tags = Map(id("healthy_soups") -> FoodImmuneData(4.0))
        )
        .get,
      1.0e-9
    )
  }

  @Test
  def carriedTagPricesUnlistedFood(): Unit = {
    assertEquals(
      4.0,
      FoodImmunity
        .resolveValue(
          id("mushroom_stew"),
          Set(id("healthy_soups")),
          items = Map.empty,
          tags = Map(id("healthy_soups") -> FoodImmuneData(4.0))
        )
        .get,
      1.0e-9
    )
  }

  @Test
  def exactEntryBeatsCarriedTag(): Unit = {
    assertEquals(
      3.0,
      FoodImmunity
        .resolveValue(
          id("modded_stew"),
          Set(id("healthy_soups")),
          items = Map(id("modded_stew") -> FoodEffectsData.immune(3.0)),
          tags = Map(id("healthy_soups") -> FoodImmuneData(4.0))
        )
        .get,
      1.0e-9
    )
  }

  @Test
  def explicitZeroItemEntryMasksCarriedTag(): Unit = {
    assertEquals(
      0.0,
      FoodImmunity
        .resolveValue(
          id("bland_stew"),
          Set(id("healthy_soups")),
          items = Map(id("bland_stew") -> FoodEffectsData.immune(0.0)),
          tags = Map(id("healthy_soups") -> FoodImmuneData(4.0))
        )
        .get,
      1.0e-9
    )
  }

  @Test
  def largestAbsoluteTagValueWinsAndTiesFavorNourishment(): Unit = {
    val tags = Map(
      id("healthy_soups") -> FoodImmuneData(4.0),
      id("suspicious_stews") -> FoodImmuneData(-2.0),
      id("mild_snacks") -> FoodImmuneData(1.0)
    )
    assertEquals(
      4.0,
      FoodImmunity
        .resolveValue(
          id("stew"),
          Set(id("healthy_soups"), id("suspicious_stews"), id("mild_snacks")),
          Map.empty,
          tags
        )
        .get,
      1.0e-9
    )

    val tied = Map(
      id("tainted_meals") -> FoodImmuneData(-2.0),
      id("hearty_meals") -> FoodImmuneData(2.0)
    )
    assertEquals(
      2.0,
      FoodImmunity
        .resolveValue(id("meal"), Set(id("tainted_meals"), id("hearty_meals")), Map.empty, tied)
        .get,
      1.0e-9
    )
  }

  @Test
  def unlistedFoodResolvesNothing(): Unit = {
    assertTrue(
      FoodImmunity
        .resolveValue(
          id("cooked_beef"),
          Set(id("healthy_soups")),
          Map.empty,
          Map(id("other_tag") -> FoodImmuneData(1.0))
        )
        .isEmpty
    )
    // Registry-backed holder lookup also comes up empty without any entries.
    assertTrue(
      FoodImmunity
        .resolve(Items.COOKED_BEEF.builtInRegistryHolder(), GameplayDataStore.Empty)
        .isEmpty
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

  private def id(path: String): Identifier = Identifier.fromNamespaceAndPath("test", path)
}
