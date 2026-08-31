package dev.krysztal.casualtiesbelow.internal.sync

import net.minecraft.SharedConstants
import net.minecraft.core.RegistryAccess
import net.minecraft.server.Bootstrap

import dev.krysztal.casualtiesbelow.internal.data.GameplayDataStore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import com.google.gson.JsonParser

final class GameplayDataSnapshotTest {

  SharedConstants.tryDetectVersion()
  Bootstrap.bootStrap()

  @Test
  def currentSchemaRoundTripsTerminalHypoxiaDuration(): Unit = {
    val original = GameplayDataSnapshot(
      maxBloodVolume = 5000.0,
      bloodOxygenHypoxiaThreshold = 50.0,
      consciousnessKnockoutThreshold = 30.0,
      unconsciousWakeThreshold = 40.0,
      shockCollapseThreshold = 0.65,
      terminalHypoxiaDurationTicks = 160,
      armorSkinFormula = "1.0",
      armorMuscleFormula = "1.0",
      maxDiscomfort = 100.0,
      discomfortLevelMeans = List(10.0, 20.0, 30.0),
      nauseaThreshold = 40.0,
      refusalThreshold = 70.0,
      vomitChanceThreshold = 60.0,
      vomitMinChancePerTick = 0.001,
      vomitMaxChancePerTick = 0.01,
      vomitRelief = 15.0,
      vomitReliefSpreadFraction = 0.2,
      gameplayData = GameplayDataStore.Empty
    )
    val json = original.toJson(RegistryAccess.EMPTY)
    val root = JsonParser.parseString(json).getAsJsonObject

    assertEquals(6, root.get("schemaVersion").getAsInt)
    assertEquals(
      original.terminalHypoxiaDurationTicks,
      root.getAsJsonObject("vitals").get("terminalHypoxiaDurationTicks").getAsInt
    )

    GameplayDataSnapshot.clearSynced()
    try {
      GameplayDataSnapshot.receive(json, RegistryAccess.EMPTY)
      assertEquals(original, GameplayDataSnapshot.current)
    } finally {
      GameplayDataSnapshot.clearSynced()
    }
  }
}
