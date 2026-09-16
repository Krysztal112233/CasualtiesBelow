package dev.krysztal.casualtiesbelow.gametest

import java.lang.Integer as JInteger

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LayeredCauldronBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluids

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction

import dev.krysztal.casualtiesbelow.block.CasualtiesBelowBlocks
import dev.krysztal.casualtiesbelow.fluid.PoppyFluids
import dev.krysztal.casualtiesbelow.item.CasualtiesBelowItems
import dev.krysztal.casualtiesbelow.item.PoppyProcessing
import dev.krysztal.casualtiesbelow.mixin.FlowingFluidInvoker
import dev.krysztal.casualtiesbelow.mixin.WaterFluidInvoker

/** In-game validation that the infusion cauldron is wired into the Transfer API as unfiltered poppy
  * liquid without weakening the filtering gate.
  */
object PoppyFluidScenarios {

  /** The infusion cauldron exposes its content to FluidStorage.SIDED as unfiltered liquid at one
    * bottle per level: extracting a bottle drains exactly one level, inserting one restores it, and
    * other poppy liquids are refused.
    */
  def infusionCauldronExposesUnfilteredStorage(helper: GameTestHelper): Unit = {
    val relative = new BlockPos(1, 1, 1)
    val full = CasualtiesBelowBlocks.PoppyInfusionCauldron
      .defaultBlockState()
      .setValue[JInteger, JInteger](
        LayeredCauldronBlock.LEVEL.nn,
        JInteger.valueOf(PoppyProcessingFullLevel)
      )
    helper.setBlock(relative, full)

    val storage =
      FluidStorage.SIDED.find(helper.getLevel, helper.absolutePos(relative), Direction.UP)
    helper.assertTrue(storage != null, "infusion cauldron must expose a sided fluid storage")

    // A different poppy liquid must not mix in.
    val reject = Transaction.openOuter()
    helper.assertTrue(
      storage.extract(
        FluidVariant.of(PoppyFluids.CrudePoppyLiquid),
        FluidConstants.BOTTLE,
        reject
      ) == 0L,
      "crude liquid must not be extractable from the infusion cauldron"
    )
    reject.abort()

    // One bottle out: exactly one level drains.
    val draw = Transaction.openOuter()
    helper.assertTrue(
      storage.extract(
        FluidVariant.of(PoppyFluids.UnfilteredPoppyLiquid),
        FluidConstants.BOTTLE,
        draw
      ) == FluidConstants.BOTTLE,
      "one bottle of unfiltered liquid must be extractable"
    )
    draw.commit()
    helper.assertTrue(
      helper
        .getBlockState(relative)
        .getValue[JInteger](LayeredCauldronBlock.LEVEL.nn)
        .intValue() == 2,
      "extracting one bottle must drain exactly one level"
    )

    // One bottle back: the level returns.
    val pour = Transaction.openOuter()
    helper.assertTrue(
      storage.insert(
        FluidVariant.of(PoppyFluids.UnfilteredPoppyLiquid),
        FluidConstants.BOTTLE,
        pour
      ) == FluidConstants.BOTTLE,
      "one bottle of unfiltered liquid must be insertable"
    )
    pour.commit()
    helper.assertTrue(
      helper
        .getBlockState(relative)
        .getValue[JInteger](LayeredCauldronBlock.LEVEL.nn)
        .intValue() == 3,
      "inserting one bottle must restore one level"
    )
    helper.succeed()
  }

  /** Buckets are the bulk tier of the unfiltered stage: a full infusion cauldron scoops into an
    * unfiltered bucket (draining to a vanilla empty cauldron), and pouring the bucket into an empty
    * cauldron recreates a full infusion cauldron. Partial cauldrons refuse the bucket.
    */
  def bucketConvertsWithInfusionCauldron(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val rel = new BlockPos(1, 1, 1)

    // A partial cauldron refuses the bucket and stays untouched.
    helper.setBlock(rel, infusionState(2))
    PoppyProcessing.useBucketScoop(
      player,
      helper.getLevel,
      helper.absolutePos(rel),
      helper.getBlockState(rel),
      new ItemStack(Items.BUCKET)
    )
    helper.assertTrue(
      helper.getBlockState(rel).is(CasualtiesBelowBlocks.PoppyInfusionCauldron),
      "a partial infusion cauldron must refuse the bucket and stay unchanged"
    )

    // Full cauldron -> bucket: drains to a vanilla empty cauldron.
    helper.setBlock(rel, infusionState(3))
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET))
    PoppyProcessing.useBucketScoop(
      player,
      helper.getLevel,
      helper.absolutePos(rel),
      helper.getBlockState(rel),
      player.getItemInHand(InteractionHand.MAIN_HAND)
    )
    helper.assertTrue(
      helper.getBlockState(rel).is(Blocks.CAULDRON),
      "scooping a full infusion cauldron must leave a vanilla empty cauldron"
    )
    helper.assertTrue(
      player.getInventory.hasAnyMatching(stack =>
        stack.getItem == CasualtiesBelowItems.UnfilteredPoppyLiquidBucket
      ),
      "scooping must hand over a filled unfiltered bucket"
    )

    // Bucket -> empty cauldron: recreates a full infusion cauldron and returns the empty bucket.
    player.setItemInHand(
      InteractionHand.MAIN_HAND,
      new ItemStack(CasualtiesBelowItems.UnfilteredPoppyLiquidBucket)
    )
    PoppyProcessing.useBucketPour(
      player,
      helper.getLevel,
      helper.absolutePos(rel),
      player.getItemInHand(InteractionHand.MAIN_HAND)
    )
    helper.assertTrue(
      helper.getBlockState(rel).is(CasualtiesBelowBlocks.PoppyInfusionCauldron) &&
        helper
          .getBlockState(rel)
          .getValue[JInteger](LayeredCauldronBlock.LEVEL.nn)
          .intValue() == 3,
      "pouring must recreate a full infusion cauldron"
    )
    helper.assertTrue(
      player.getInventory.hasAnyMatching(stack => stack.getItem == Items.BUCKET),
      "pouring must return an empty bucket"
    )
    helper.succeed()
  }

  /** The static FlowingFluidInvoker must route to the vanilla protected legacy-level mapping that
    * encodes fluid states into LiquidBlock.LEVEL: source -> 0, flowing amounts mirror, falling adds 8.
    */
  def flowingFluidInvokerMapsLegacyLevels(helper: GameTestHelper): Unit = {
    helper.assertTrue(
      FlowingFluidInvoker.callGetLegacyLevel(
        PoppyFluids.UnfilteredPoppyLiquid.getSource(false)
      ) == 0,
      "a source must map to legacy level 0"
    )
    helper.assertTrue(
      FlowingFluidInvoker.callGetLegacyLevel(
        PoppyFluids.FlowingUnfilteredPoppyLiquid.getFlowing(7, false)
      ) == 1,
      "flowing amount 7 must map to legacy level 1"
    )
    helper.assertTrue(
      FlowingFluidInvoker.callGetLegacyLevel(
        PoppyFluids.FlowingUnfilteredPoppyLiquid.getFlowing(8, true)
      ) == 8,
      "falling fluid must map to legacy level 8"
    )
    helper.succeed()
  }

  /** The instance WaterFluidInvoker must route to the vanilla water implementation of
    * beforeDestroyingBlock: invoking it against a block-entity state must run without throwing (a
    * broken mixin would hit the stub AssertionError).
    */
  def waterFluidInvokerDelegatesDropLogic(helper: GameTestHelper): Unit = {
    val pos = helper.absolutePos(new BlockPos(1, 1, 1))
    Fluids.WATER
      .asInstanceOf[WaterFluidInvoker]
      .casualtiesbelow$invokeBeforeDestroyingBlock(
        helper.getLevel,
        pos,
        Blocks.CHEST.defaultBlockState()
      )
    helper.succeed()
  }

  private def infusionState(level: Int): BlockState =
    CasualtiesBelowBlocks.PoppyInfusionCauldron
      .defaultBlockState()
      .setValue[JInteger, JInteger](
        LayeredCauldronBlock.LEVEL.nn,
        JInteger.valueOf(level)
      )

  private val PoppyProcessingFullLevel: Int = 3
}
