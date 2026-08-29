package dev.krysztal.casualtiesbelow.api.wound

import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.JsonOps
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap

import dev.krysztal.casualtiesbelow.api.data.WoundRuleData

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.google.gson.JsonParser

final class WoundDataCodecTest {

  SharedConstants.tryDetectVersion()
  Bootstrap.bootStrap()

  @Test
  def legacyLinearProfileDefaultsAndReencodesItsType(): Unit = {
    val profile = decode(
      WoundProfile.Codec,
      """{
        |  "skin_per_point": 2.0,
        |  "muscle_per_point": 3.0,
        |  "bleed_rate_per_wound": 0.2,
        |  "pain_per_point": 4.0
        |}""".stripMargin
    )

    assertEquals(WoundProfile.LinearType, profile.profileType)
    val encoded = WoundProfile.Codec.encodeStart(JsonOps.INSTANCE, profile).getOrThrow()
    assertEquals(WoundProfile.LinearType.toString, encoded.getAsJsonObject.get("type").getAsString)
  }

  @Test
  def v2RuleDecodesMixedSelectorsAndOrderedApplications(): Unit = {
    val decoded = decode(
      WoundRuleData.Codec,
      """{
        |  "match": {
        |    "damage_types": ["minecraft:fall", "#casualtiesbelow:fall_impacts"],
        |    "excluded_damage_types": "example:harmless_fall"
        |  },
        |  "applications": [
        |    {
        |      "type": "casualtiesbelow:localized",
        |      "target": {"type": "casualtiesbelow:fixed", "part": "head"},
        |      "wounds": [{
        |        "profile": "casualtiesbelow:prick",
        |        "hemostasis": {"chance": 0.2, "reduction_fraction": 0.25}
        |      }]
        |    },
        |    {
        |      "type": "casualtiesbelow:scatter",
        |      "min_count": 2,
        |      "max_count": 4,
        |      "wounds": [{"profile": "casualtiesbelow:blast", "severity_multiplier": 0.5}]
        |    },
        |    {
        |      "type": "casualtiesbelow:paired_impact",
        |      "primary": {
        |        "type": "casualtiesbelow:weighted",
        |        "weights": {"leg_left": 1.0, "leg_right": 1.0}
        |      },
        |      "paired": {"fraction": 1.0},
        |      "wounds": [{"profile": "casualtiesbelow:fall"}]
        |    }
        |  ],
        |  "priority": 250
        |}""".stripMargin
    )

    val rule = decoded
    assertEquals(3, rule.applications.size)
    val localized = assertInstanceOf(classOf[LocalizedApplicationData], rule.applications.head)
    val hemostasis = localized.wounds.head.hemostasis.orElseThrow()
    assertEquals(0.2, hemostasis.chance, 1.0e-9)
    assertEquals(0.25, hemostasis.reductionFraction, 1.0e-9)
    val scatter = assertInstanceOf(classOf[ScatterApplicationData], rule.applications(1))
    assertTrue(scatter.wounds.head.hemostasis.isEmpty)
    assertInstanceOf(classOf[PairedImpactApplicationData], rule.applications(2))
    assertEquals(2, rule.woundMatch.damageTypes.orElseThrow().entries.size)
    assertEquals(250, rule.priority.intValue())
  }

  @Test
  def malformedApplicationsFailDuringDecode(): Unit = {
    assertDecodeFails(
      WoundRuleData.Codec,
      """{"applications":[]}"""
    )
    assertDecodeFails(
      WoundRuleData.Codec,
      """{
        |  "applications": [{
        |    "type": "casualtiesbelow:scatter",
        |    "min_count": 5,
        |    "max_count": 2,
        |    "wounds": [{"profile": "casualtiesbelow:blast"}]
        |  }]
        |}""".stripMargin
    )
    assertDecodeFails(
      WoundRuleData.Codec,
      """{
        |  "applications": [{
        |    "type": "casualtiesbelow:localized",
        |    "wounds": [{
        |      "profile": "casualtiesbelow:burn",
        |      "hemostasis": {"chance": 0.0, "reduction_fraction": 0.25}
        |    }]
        |  }]
        |}""".stripMargin
    )
    assertDecodeFails(
      WoundRuleData.Codec,
      """{
        |  "applications": [{
        |    "type": "casualtiesbelow:paired_impact",
        |    "primary": {"type": "casualtiesbelow:fixed", "part": "leg_left"},
        |    "condition_ladder": [{
        |      "condition": "fracture",
        |      "at_least": 0.0,
        |      "pain": 10.0,
        |      "base_recovery_ticks": 200
        |    }],
        |    "wounds": [{"profile": "casualtiesbelow:fall"}]
        |  }]
        |}""".stripMargin
    )
  }

  private def decode[T](codec: Codec[T], json: String): T = {
    codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow()
  }

  private def assertDecodeFails[T](codec: Codec[T], json: String): Unit = {
    val result: DataResult[T] = codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
    assertFalse(result.result().isPresent)
    assertTrue(result.error().isPresent)
  }
}
