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

final class FoodImmuneDataTest {

  SharedConstants.tryDetectVersion()
  Bootstrap.bootStrap()

  @Test
  def valueDecodesFromImmuneField(): Unit = {
    assertEquals(2.0, decode("""{"immune": 2.0}""").immune, 1.0e-9)
    assertEquals(-3.0, decode("""{"immune": -3.0}""").immune, 1.0e-9)
    assertEquals(0.0, decode("""{"immune": 0.0}""").immune, 1.0e-9)
  }

  @Test
  def nonFiniteAmountsFailOnDecodeAndEncode(): Unit = {
    List(Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity).foreach { amount =>
      val json = new JsonObject
      json.add("immune", new JsonPrimitive(amount))
      val decoded = FoodImmuneData.Codec.parse(JsonOps.INSTANCE, json)
      assertFalse(decoded.result().isPresent, s"decode unexpectedly accepted $amount")

      val encoded = FoodImmuneData.Codec.encodeStart(JsonOps.INSTANCE, FoodImmuneData(amount))
      assertFalse(encoded.result().isPresent, s"encode unexpectedly accepted $amount")
    }
  }

  @Test
  def immuneFieldIsRequired(): Unit = {
    assertDecodeFails("""{}""")
  }

  @Test
  def valueRoundTripsThroughJson(): Unit = {
    val original = FoodImmuneData(4.0)
    val encoded = FoodImmuneData.Codec.encodeStart(JsonOps.INSTANCE, original).getOrThrow()
    assertEquals(original, FoodImmuneData.Codec.parse(JsonOps.INSTANCE, encoded).getOrThrow())
  }

  private def decode(json: String): FoodImmuneData = {
    FoodImmuneData.Codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow()
  }

  private def assertDecodeFails(json: String): Unit = {
    val result: DataResult[FoodImmuneData] =
      FoodImmuneData.Codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
    assertFalse(result.result().isPresent)
    assertTrue(result.error().isPresent)
  }
}
