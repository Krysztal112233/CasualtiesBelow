package dev.krysztal.casualtiesbelow.adrenaline

import com.mojang.serialization.DataResult
import com.mojang.serialization.JsonOps
import net.minecraft.SharedConstants
import net.minecraft.resources.Identifier
import net.minecraft.server.Bootstrap

import dev.krysztal.casualtiesbelow.api.data.AdrenalineRuleData
import dev.krysztal.casualtiesbelow.api.data.WoundMatchData

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

final class AdrenalineRuleDataTest {

  SharedConstants.tryDetectVersion()
  Bootstrap.bootStrap()

  @Test
  def explicitMatchAndAmountAreRequiredWhileZeroIsValid(): Unit = {
    val zero = decode("""{"match":{},"amount":0.0}""")
    assertEquals(WoundMatchData.Empty, zero.damageMatch)
    assertEquals(0.0, zero.amount, 1.0e-9)
    assertEquals(0, zero.priority.intValue())

    assertDecodeFails("""{"amount":1.0}""")
    assertDecodeFails("""{"match":{}}""")
  }

  @Test
  def invalidAmountsFailOnDecodeAndEncode(): Unit = {
    List(-1.0, Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity).foreach { amount =>
      val json = new JsonObject
      json.add("match", new JsonObject)
      json.add("amount", new JsonPrimitive(amount))
      val decoded = AdrenalineRuleData.Codec.parse(JsonOps.INSTANCE, json)
      assertFalse(decoded.result().isPresent, s"decode unexpectedly accepted $amount")

      val rule = AdrenalineRuleData(WoundMatchData.Empty, amount, 0)
      val encoded = AdrenalineRuleData.Codec.encodeStart(JsonOps.INSTANCE, rule)
      assertFalse(encoded.result().isPresent, s"encode unexpectedly accepted $amount")
    }
  }

  @Test
  def resolverUsesPriorityThenIdAndZeroStopsFallback(): Unit = {
    val low = id("low") -> rule(10.0, Int.MinValue)
    val alphabeticFirst = id("a") -> rule(20.0, Int.MaxValue)
    val alphabeticSecond = id("b") -> rule(30.0, Int.MaxValue)
    val selected = AdrenalineRules.select(Map(low, alphabeticSecond, alphabeticFirst))(_ => true)

    assertEquals(Some(ClassifiedAdrenalineRule(alphabeticFirst._1, 20.0)), selected)

    val disabled = id("disabled") -> rule(0.0, 100)
    val fallback = id("fallback") -> rule(50.0, 0)
    assertEquals(
      Some(ClassifiedAdrenalineRule(disabled._1, 0.0)),
      AdrenalineRules.select(Map(disabled, fallback))(_ => true)
    )
    assertEquals(
      Some(ClassifiedAdrenalineRule(fallback._1, 50.0)),
      AdrenalineRules.select(Map(disabled, fallback))(_.amount > 0.0)
    )
  }

  private def decode(json: String): AdrenalineRuleData = {
    AdrenalineRuleData.Codec
      .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
      .getOrThrow()
  }

  private def assertDecodeFails(json: String): Unit = {
    val result: DataResult[AdrenalineRuleData] =
      AdrenalineRuleData.Codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
    assertFalse(result.result().isPresent)
    assertTrue(result.error().isPresent)
  }

  private def rule(amount: Double, priority: Int): AdrenalineRuleData =
    AdrenalineRuleData(WoundMatchData.Empty, amount, priority)

  private def id(path: String): Identifier =
    Identifier.fromNamespaceAndPath("test", path)
}
