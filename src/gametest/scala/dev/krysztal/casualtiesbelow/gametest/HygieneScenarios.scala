package dev.krysztal.casualtiesbelow.gametest

import java.lang.Integer as JInteger

import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LayeredCauldronBlock

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.extension.ConfigValueExtensions.*
import dev.krysztal.casualtiesbelow.internal.extension.PlayerExtensions.*
import dev.krysztal.casualtiesbelow.item.CasualtiesBelowDataComponents
import dev.krysztal.casualtiesbelow.item.CasualtiesBelowItems
import dev.krysztal.casualtiesbelow.item.InjectionSettlement
import dev.krysztal.casualtiesbelow.item.LiquidContents
import dev.krysztal.casualtiesbelow.item.SyringeContents
import dev.krysztal.casualtiesbelow.physiology.hygiene.Dirtiness

/** In-game validation of dirtiness progression: passive accrual and cauldron washing. */
object HygieneScenarios {

  /** An idle survival player accumulates dirtiness at exactly the configured base rate. */
  def passiveAccrualAccumulates(helper: GameTestHelper): Unit = {
    // Pin the weather to clear so rain washing does not mask the base accrual.
    helper.getLevel.setRainLevel(0.0f)
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val vitals = player.vitals
    (1 to 100).foreach(_ => Dirtiness.tickForGameTest(player))
    val expected = CasualtiesBelowConfig.DirtinessAccrualPerSecond.value / 20.0 * 100
    helper.assertTrue(
      math.abs(vitals.dirtiness - expected) < 1.0e-9,
      s"100 ticks of base accrual: expected $expected, got ${vitals.dirtiness}"
    )
    helper.succeed()
  }

  /** Standing in a water cauldron washes at the immersion rate and consumes one water level per
    * cauldronPointsPerLevel washed — never before the crossing, always at it.
    */
  def cauldronSoakWashesAndConsumesLevels(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val vitals = player.vitals
    VitalsMutations.setDirtiness(vitals, 60.0)

    val relative = new BlockPos(1, 1, 1)
    val full = Blocks.WATER_CAULDRON
      .defaultBlockState()
      .setValue[JInteger, JInteger](LayeredCauldronBlock.LEVEL.nn, JInteger.valueOf(3))
    helper.setBlock(relative, full)
    val absolute = helper.absolutePos(relative)

    val perTick = CasualtiesBelowConfig.DirtinessWashWaterPerSecond.value / 20.0
    val pointsPerLevel = CasualtiesBelowConfig.DirtinessCauldronPointsPerLevel.value
    val ticksPerLevel = math.ceil(pointsPerLevel / perTick).toInt

    // One tick short of the crossing: the level must be untouched.
    (1 until ticksPerLevel).foreach { _ =>
      Dirtiness.onCauldronSoak(player, full, helper.getLevel, absolute)
    }
    val washedSoFar = perTick * (ticksPerLevel - 1)
    helper.assertTrue(
      math.abs(vitals.dirtiness - (60.0 - washedSoFar)) < 0.01,
      s"cauldron wash rate: expected ${60.0 - washedSoFar}, got ${vitals.dirtiness}"
    )
    helper.assertTrue(
      helper.getLevel.getBlockState(absolute).getValue(LayeredCauldronBlock.LEVEL) == 3,
      "cauldron level must not drop before the crossing"
    )

    // The crossing tick consumes exactly one level.
    Dirtiness.onCauldronSoak(player, full, helper.getLevel, absolute)
    helper.assertTrue(
      helper.getLevel.getBlockState(absolute).getValue(LayeredCauldronBlock.LEVEL) == 2,
      "cauldron level must drop by one at the crossing"
    )
    helper.succeed()
  }

  /** Standing in real water washes at the immersion rate. `FakePlayer.tick` is a no-op, so
    * `baseTick` is driven manually once to refresh the water-touching flag before the manual
    * hygiene ticks.
    */
  def waterWashRemovesDirtiness(helper: GameTestHelper): Unit = {
    helper.getLevel.setRainLevel(0.0f)
    helper.setBlock(new BlockPos(1, 1, 1), Blocks.WATER)
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val waterPos = helper.absolutePos(new BlockPos(1, 1, 1))
    player.setPos(waterPos.getX + 0.5, waterPos.getY + 0.1, waterPos.getZ + 0.5)

    val vitals = player.vitals
    VitalsMutations.setDirtiness(vitals, 5.0)
    player.baseTick()
    helper.assertTrue(player.isInWater, "fake player should register as in water after baseTick")
    (1 to 20).foreach(_ => Dirtiness.tickForGameTest(player))

    // 20 ticks at 4.8/s wash minus passive accrual must remove ~4.8 points from 5.0.
    helper.assertTrue(
      vitals.dirtiness < 1.0,
      s"water wash should nearly clean the player, got ${vitals.dirtiness}"
    )
    helper.succeed()
  }

  /** A dirty needle seeds the injected limb's infection in proportion to dirtiness and pushed
    * amount; a clean needle seeds nothing.
    */
  def dirtyInjectionSeedsInfection(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val syringe = new ItemStack(CasualtiesBelowItems.CalibratedSyringe)
    syringe.set(
      CasualtiesBelowDataComponents.SyringeContentsComponent,
      SyringeContents(
        LiquidContents.RefinedPoppyExtract.liquid,
        LiquidContents.AmpouleDroplets,
        10.0
      )
    )
    player.setItemInHand(InteractionHand.MAIN_HAND, syringe)

    val maxDirtiness = CasualtiesBelowConfig.MaxDirtiness.value
    VitalsMutations.setDirtiness(player.vitals, maxDirtiness / 2)
    val body = CasualtiesBelowComponents.Body.get(player)
    // A main-hand syringe pricks the opposite arm (right-handed default: the left arm).
    val injected = BodyPart.ArmLeft

    InjectionSettlement.applyBatch(
      player,
      InteractionHand.MAIN_HAND,
      LiquidContents.RefinedPoppyExtract.liquid,
      LiquidContents.AmpouleDroplets,
      LiquidContents.AmpouleDroplets / 2,
      0.0
    )

    val expected = CasualtiesBelowConfig.DirtinessInjectionSeedAtMax.value * 0.5 * 0.5
    val progress = body.stats(injected).infectionProgress
    helper.assertTrue(progress.isPresent, "dirty injection must seed an infection")
    helper.assertTrue(
      math.abs(progress.getAsDouble - expected) < 1.0e-9,
      s"seeded progress: expected $expected, got ${progress.getAsDouble}"
    )
    helper.succeed()
  }
}
