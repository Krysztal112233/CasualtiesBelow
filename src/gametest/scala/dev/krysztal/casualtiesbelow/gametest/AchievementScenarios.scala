package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.advancements.AdvancementHolder
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi
import dev.krysztal.casualtiesbelow.api.CasualtiesBelowDamageTypes
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.api.body.vitals.ConsciousnessSnapshot
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.component.MutableLimbState
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.extension.PlayerExtensions.*
import dev.krysztal.casualtiesbelow.item.CasualtiesBelowItems
import dev.krysztal.casualtiesbelow.physiology.discomfort.Discomfort
import dev.krysztal.casualtiesbelow.progression.AchievementHooks

/** In-game validation of the advancement tree: triggers fire from their physiological hook points
  * and awards land on the generated advancement holders.
  */
object AchievementScenarios {

  private def holder(helper: GameTestHelper, path: String): AdvancementHolder = {
    val found = helper.getLevel.getServer.getAdvancements.get(CasualtiesBelowApi.id(path))
    helper.assertTrue(found != null, s"advancement casualtiesbelow:$path should be loaded")
    found
  }

  private def assertDone(helper: GameTestHelper, player: ServerPlayer, path: String): Unit = {
    val progress = player.getAdvancements.getOrStartProgress(holder(helper, path))
    helper.assertTrue(progress.isDone, s"advancement casualtiesbelow:$path should be done")
  }

  private def assertNotDone(helper: GameTestHelper, player: ServerPlayer, path: String): Unit = {
    val progress = player.getAdvancements.getOrStartProgress(holder(helper, path))
    helper.assertTrue(!progress.isDone, s"advancement casualtiesbelow:$path should not be done")
  }

  /** Picking up a bandage fires the vanilla inventory-changed criterion. */
  def bandageObtainedGrantsAdvancement(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    // Isolated test players never pass PlayerList login, so wire the inventory-change listener
    // that real players get from ServerPlayer.initInventoryMenu.
    player.initInventoryMenu()
    player.getInventory.setItem(0, new ItemStack(CasualtiesBelowItems.BasicBandage))
    player.inventoryMenu.broadcastChanges()
    assertDone(helper, player, "bandage")
    helper.succeed()
  }

  /** Same path for the refined extract: any acquisition counts as a successful synthesis. */
  def refinedExtractObtainedGrantsAdvancement(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    player.initInventoryMenu()
    player.getInventory.setItem(0, new ItemStack(CasualtiesBelowItems.RefinedPoppyExtract))
    player.inventoryMenu.broadcastChanges()
    assertDone(helper, player, "refined_extract")
    helper.succeed()
  }

  /** Dying of hypoxia while opioids suppress respiration is an overdose death. */
  def opioidOverdoseDeathGrantsAdvancement(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    VitalsMutations.setOpioidLevel(player.vitals, 200.0)
    player.hurtServer(
      helper.getLevel,
      CasualtiesBelowDamageTypes.hypoxia(helper.getLevel),
      Float.MaxValue
    )
    helper.assertTrue(!player.isAlive, "player should have died")
    assertDone(helper, player, "opioid_overdose_death")
    helper.succeed()
  }

  /** The same hypoxia death with clean blood is an ordinary suffocation, not an overdose. */
  def hypoxiaDeathWithoutOpioidDoesNotGrant(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    player.hurtServer(
      helper.getLevel,
      CasualtiesBelowDamageTypes.hypoxia(helper.getLevel),
      Float.MaxValue
    )
    helper.assertTrue(!player.isAlive, "player should have died")
    assertNotDone(helper, player, "opioid_overdose_death")
    helper.succeed()
  }

  /** A player whose consciousness reaches literal zero earns the achievement on the next poll. */
  def consciousnessMinimumGrantsAdvancement(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    VitalsMutations.applyConsciousnessState(
      player.vitals,
      ConsciousnessSnapshot(0.0, true)
    )
    AchievementHooks.tickForGameTest(player)
    assertDone(helper, player, "consciousness_minimum")
    helper.succeed()
  }

  def consciousPlayerDoesNotGrantMinimum(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    AchievementHooks.tickForGameTest(player)
    assertNotDone(helper, player, "consciousness_minimum")
    helper.succeed()
  }

  /** Eating revolting food while already at maximum discomfort fires the achievement. */
  def maxDiscomfortFoodGrantsAdvancement(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    VitalsMutations.setDiscomfort(
      player.vitals,
      CasualtiesBelowConfig.discomfort.maxValue.get()
    )
    Discomfort.onFoodEaten(player, new ItemStack(Items.ROTTEN_FLESH))
    assertDone(helper, player, "max_discomfort_food")
    helper.succeed()
  }

  /** Food without discomfort data never fires it, no matter how queasy the player is. */
  def ordinaryFoodDoesNotGrantMaxDiscomfort(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    VitalsMutations.setDiscomfort(
      player.vitals,
      CasualtiesBelowConfig.discomfort.maxValue.get()
    )
    Discomfort.onFoodEaten(player, new ItemStack(Items.APPLE))
    assertNotDone(helper, player, "max_discomfort_food")
    helper.succeed()
  }

  /** The root advancement is earned: first external bleeding opens the tab (vanilla style). */
  def firstBleedingGrantsRoot(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    assertNotDone(helper, player, "root")
    setBleeding(player, BodyPart.Head, 0.1)
    AchievementHooks.tickForGameTest(player)
    assertDone(helper, player, "root")
    helper.succeed()
  }

  /** Near-maximum bleeding that is later fully stopped completes the "Not Today" episode. */
  def hemostasisAfterNearMaxBleedingGrantsAdvancement(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val nearMaxRate = CasualtiesBelowConfig.bleeding.maxExternalBleedingRate.get() *
      CasualtiesBelowConfig.bleeding.notTodayNearMaxBleedingFraction.get()
    setBleeding(player, BodyPart.Head, nearMaxRate)
    AchievementHooks.tickForGameTest(player)
    assertNotDone(helper, player, "hemostasis")
    setBleeding(player, BodyPart.Head, 0.0)
    AchievementHooks.tickForGameTest(player)
    assertDone(helper, player, "hemostasis")
    helper.succeed()
  }

  /** Bleeding that never reached the near-maximum threshold does not count, even when stopped. */
  def bleedingBelowThresholdDoesNotGrantHemostasis(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val nearMaxRate = CasualtiesBelowConfig.bleeding.maxExternalBleedingRate.get() *
      CasualtiesBelowConfig.bleeding.notTodayNearMaxBleedingFraction.get()
    setBleeding(player, BodyPart.Head, nearMaxRate * 0.5)
    AchievementHooks.tickForGameTest(player)
    setBleeding(player, BodyPart.Head, 0.0)
    AchievementHooks.tickForGameTest(player)
    assertNotDone(helper, player, "hemostasis")
    helper.succeed()
  }

  private def setBleeding(player: ServerPlayer, part: BodyPart, rate: Double): Unit = {
    val body = CasualtiesBelowComponents.Body.get(player)
    val state = MutableLimbState.from(body.stats(part))
    state.externalBleedingRate = rate
    BodyMutations.replace(player, part, state, markDirty = false)
  }
}
