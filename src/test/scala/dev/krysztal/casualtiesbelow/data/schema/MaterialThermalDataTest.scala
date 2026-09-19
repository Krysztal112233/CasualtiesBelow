package dev.krysztal.casualtiesbelow.data.schema

import com.mojang.serialization.JsonOps
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

final class MaterialThermalDataTest {

  SharedConstants.tryDetectVersion()
  Bootstrap.bootStrap()

  @Test
  def decodesFullyPopulatedEntry(): Unit = {
    val data = decode(
      """{"insulation": 0.6, "dissipation_block": 0.5, "fire_resistance": 0.7}"""
    )
    assertEquals(0.6, data.insulation, 1.0e-9)
    assertEquals(0.5, data.dissipationBlock, 1.0e-9)
    assertEquals(0.7, data.fireResistance, 1.0e-9)
  }

  @Test
  def missingFireResistanceDefaultsToZero(): Unit = {
    val data = decode("""{"insulation": 0.1, "dissipation_block": 0.05}""")
    assertEquals(0.0, data.fireResistance, 1.0e-9)
  }

  @Test
  def outOfRangeCoefficientsAreRejected(): Unit = {
    List(-0.1, 1.1, Double.NaN, Double.PositiveInfinity).foreach { value =>
      val json = new JsonObject
      json.add("insulation", new JsonPrimitive(value))
      json.add("dissipation_block", new JsonPrimitive(0.5))
      val decoded = MaterialThermalData.Codec.parse(JsonOps.INSTANCE, json)
      assertFalse(decoded.result().isPresent, s"decode unexpectedly accepted $value")
    }
  }

  @Test
  def missingRequiredFieldsAreRejected(): Unit = {
    assertFalse(decodeResult("""{"dissipation_block": 0.5}""").result().isPresent)
    assertFalse(decodeResult("""{"insulation": 0.5}""").result().isPresent)
  }

  private def decode(json: String): MaterialThermalData =
    decodeResult(json).result().orElseThrow()

  private def decodeResult(json: String) =
    MaterialThermalData.Codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
}
