package dev.krysztal.casualtiesbelow.data.schema

import com.mojang.serialization.DataResult
import com.mojang.serialization.JsonOps
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

final class FoodEffectsDataTest {

  SharedConstants.tryDetectVersion()
  Bootstrap.bootStrap()

  @Test
  def immuneOnlyEntryDecodes(): Unit = {
    val decoded = decode("""{"immune": 2.0}""")
    assertEquals(2.0, decoded.immune.get(), 1.0e-9)
    assertTrue(decoded.discomfortTier.isEmpty)
    assertTrue(decoded.discomfortMean.isEmpty)
  }

  @Test
  def discomfortFieldsDecodeAndAreMutuallyExclusive(): Unit = {
    assertEquals(3, decode("""{"discomfort_tier": 3}""").discomfortTier.get().intValue())
    assertEquals(15.0, decode("""{"discomfort_mean": 15.0}""").discomfortMean.get(), 1.0e-9)
    assertDecodeFails("""{"discomfort_tier": 2, "discomfort_mean": 15.0}""")
  }

  @Test
  def combinedEntryDecodes(): Unit = {
    val decoded = decode("""{"immune": -3.0, "discomfort_tier": 3}""")
    assertEquals(-3.0, decoded.immune.get(), 1.0e-9)
    assertEquals(3, decoded.discomfortTier.get().intValue())
  }

  @Test
  def emptyEntryIsRejected(): Unit = {
    assertDecodeFails("""{}""")
  }

  @Test
  def invalidValuesAreRejected(): Unit = {
    // Non-finite immune values.
    List(Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity).foreach { amount =>
      val json = new JsonObject
      json.add("immune", new JsonPrimitive(amount))
      val decoded = FoodEffectsData.Codec.parse(JsonOps.INSTANCE, json)
      assertFalse(decoded.result().isPresent, s"decode unexpectedly accepted $amount")
    }
    // Tier outside 1-3, negative mean.
    assertDecodeFails("""{"discomfort_tier": 0}""")
    assertDecodeFails("""{"discomfort_tier": 4}""")
    assertDecodeFails("""{"discomfort_mean": -1.0}""")
  }

  @Test
  def entryRoundTripsThroughJson(): Unit = {
    val original = decode("""{"immune": -1.5, "discomfort_mean": 25.0}""")
    val encoded = FoodEffectsData.Codec.encodeStart(JsonOps.INSTANCE, original).getOrThrow()
    assertEquals(original, FoodEffectsData.Codec.parse(JsonOps.INSTANCE, encoded).getOrThrow())
  }

  private def decode(json: String): FoodEffectsData = {
    FoodEffectsData.Codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow()
  }

  private def assertDecodeFails(json: String): Unit = {
    val result: DataResult[FoodEffectsData] =
      FoodEffectsData.Codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
    assertFalse(result.result().isPresent)
    assertTrue(result.error().isPresent)
  }
}
