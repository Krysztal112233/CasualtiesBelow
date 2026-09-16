package dev.krysztal.casualtiesbelow.item

import java.lang.Integer as JInteger
import java.util.List

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.stats.Stats
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.Potions
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LayeredCauldronBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.phys.BlockHitResult

import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.transfer.v1.fluid.CauldronFluidContent
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants

import dev.krysztal.casualtiesbelow.block.CasualtiesBelowBlocks
import dev.krysztal.casualtiesbelow.fluid.PoppyFluids

/** Server-authoritative, no-GUI poppy processing on vanilla-shaped cauldrons. */
private[casualtiesbelow] object PoppyProcessing {
  private[casualtiesbelow] val FullCauldronLevel = LayeredCauldronBlock.MAX_FILL_LEVEL
  private[casualtiesbelow] val SoakDurationTicks = 12000
  private[casualtiesbelow] val CrudeFilterMaxStack = 16
  private[casualtiesbelow] val LiquidContainerMaxStack = 1

  def register(): Unit = {
    UseBlockCallback.EVENT.register(new UseBlockCallback {
      override def interact(
          player: Player,
          level: Level,
          hand: InteractionHand,
          hitResult: BlockHitResult
      ): InteractionResult = PoppyProcessing.interact(player, level, hand, hitResult)
    })
    // Using the filter in hand (away from an infusion cauldron) filters an unfiltered bottle from
    // the inventory at the same cost as cauldron filtering.
    UseItemCallback.EVENT.register(new UseItemCallback {
      override def interact(
          player: Player,
          level: Level,
          hand: InteractionHand
      ): InteractionResult = PoppyProcessing.filterBottle(player, level, hand)
    })

    // Expose the infusion cauldron to fluid pipes as the unfiltered liquid, one bottle per level.
    // Filtering remains the only unfiltered-to-crude conversion, so automation can move the
    // liquid around but never skips the filter cost.
    CauldronFluidContent.registerCauldron(
      CasualtiesBelowBlocks.PoppyInfusionCauldron,
      PoppyFluids.UnfilteredPoppyLiquid,
      FluidConstants.BOTTLE,
      LayeredCauldronBlock.LEVEL
    )
  }

  private def interact(
      player: Player,
      level: Level,
      hand: InteractionHand,
      hitResult: BlockHitResult
  ): InteractionResult = {
    if (player.isSpectator()) return InteractionResult.PASS

    val pos = hitResult.getBlockPos
    val state = level.getBlockState(pos)
    val heldStack = player.getItemInHand(hand)

    if (
      heldStack.getItem == CasualtiesBelowItems.CrudeFilter &&
      state.is(CasualtiesBelowBlocks.PoppyInfusionCauldron)
    ) {
      return useFilter(player, level, pos, state, heldStack)
    }

    // Bulk tier of the unfiltered stage: a full infusion cauldron can be scooped into a bucket,
    // and a bucket can be poured into an empty cauldron to recreate one (1 bucket = 3 levels).
    if (
      heldStack.getItem == Items.BUCKET &&
      state.is(CasualtiesBelowBlocks.PoppyInfusionCauldron)
    ) {
      return useBucketScoop(player, level, pos, state, heldStack)
    }
    if (
      heldStack.getItem == CasualtiesBelowItems.UnfilteredPoppyLiquidBucket &&
      state.is(Blocks.CAULDRON)
    ) {
      return useBucketPour(player, level, pos, heldStack)
    }

    InteractionResult.PASS
  }

  /** Consumes one paste entity only after it has entered a currently-full water cauldron. */
  private[casualtiesbelow] def tryStartSoakFromDroppedItem(
      level: Level,
      pos: BlockPos,
      itemEntity: ItemEntity
  ): Boolean = {
    // Vanilla defers burning-entity cauldron handling until after this callback. Let it
    // extinguish the stack first so its captured water state cannot overwrite a new batch.
    if (level.isClientSide() || itemEntity.isOnFire()) return false

    val serverLevel = level.asInstanceOf[ServerLevel]
    val state = serverLevel.getBlockState(pos)
    val droppedStack = itemEntity.getItem
    if (
      !isFullWaterCauldron(state) ||
      droppedStack.isEmpty ||
      droppedStack.getItem != CasualtiesBelowItems.CrudePoppyPaste
    ) return false

    val owner = itemEntity.getOwner
    if (
      !serverLevel.setBlockAndUpdate(
        pos,
        CasualtiesBelowBlocks.SoakingPoppyCauldron.defaultBlockState()
      )
    ) return false

    val remainder = remainingAfterConsumingOne(droppedStack)
    if (remainder.isEmpty) itemEntity.discard()
    else ejectRemainder(serverLevel, pos, itemEntity, remainder)

    owner match {
      case player: ServerPlayer =>
        player.awardStat(Stats.ITEM_USED.get(CasualtiesBelowItems.CrudePoppyPaste))
        player.awardStat(Stats.USE_CAULDRON)
      case _ =>
    }
    serverLevel.playSound(
      null,
      pos,
      SoundEvents.MUD_PLACE,
      SoundSource.BLOCKS,
      1.0f,
      1.0f
    )
    serverLevel.gameEvent(owner, GameEvent.BLOCK_CHANGE, pos)
    true
  }

  private def ejectRemainder(
      level: ServerLevel,
      pos: BlockPos,
      itemEntity: ItemEntity,
      remainder: ItemStack
  ): Unit = {
    val direction = Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom)
    itemEntity.setItem(remainder)
    itemEntity.setPos(pos.getX + 0.5, pos.getY + 1.1, pos.getZ + 0.5)
    itemEntity.setDeltaMovement(
      direction.getStepX * 0.2,
      0.2,
      direction.getStepZ * 0.2
    )
  }

  private[item] def remainingAfterConsumingOne(stack: ItemStack): ItemStack =
    if (stack.isEmpty || stack.getCount <= 1) ItemStack.EMPTY
    else stack.copyWithCount(stack.getCount - 1)

  private def useFilter(
      player: Player,
      level: Level,
      pos: BlockPos,
      state: BlockState,
      filter: ItemStack
  ): InteractionResult = {
    if (level.isClientSide()) return InteractionResult.SUCCESS

    val inventory = player.getInventory
    val waterBottleSlot = findWaterBottleSlot(inventory.getNonEquipmentItems)
    if (waterBottleSlot < 0) {
      player.sendOverlayMessage(
        Component.translatable("message.casualtiesbelow.poppy.water_bottle_required")
      )
      return InteractionResult.SUCCESS
    }

    filter.consume(1, player)
    val output = new ItemStack(CasualtiesBelowItems.CrudePoppyLiquid)
    if (player.hasInfiniteMaterials()) {
      if (!inventory.add(output)) player.drop(output, false)
    } else {
      inventory.setItem(waterBottleSlot, output)
    }

    LayeredCauldronBlock.lowerFillLevel(state, level, pos)
    player.awardStat(Stats.ITEM_USED.get(CasualtiesBelowItems.CrudeFilter))
    player.awardStat(Stats.USE_CAULDRON)
    level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f)
    level.gameEvent(player, GameEvent.FLUID_PICKUP, pos)
    InteractionResult.SUCCESS
  }

  /** Filters one unfiltered bottle from the inventory into a crude bottle. Only reachable through
    * the UseItemCallback above: clicking the infusion cauldron with a filter takes the block path
    * first, so cauldron filtering is unaffected.
    */
  private def filterBottle(
      player: Player,
      level: Level,
      hand: InteractionHand
  ): InteractionResult = {
    if (player.isSpectator()) return InteractionResult.PASS

    val filter = player.getItemInHand(hand)
    if (filter.getItem != CasualtiesBelowItems.CrudeFilter) return InteractionResult.PASS
    if (level.isClientSide()) return InteractionResult.SUCCESS

    val inventory = player.getInventory
    val bottleSlot = findUnfilteredBottleSlot(inventory.getNonEquipmentItems)
    if (bottleSlot < 0) {
      player.sendOverlayMessage(
        Component.translatable("message.casualtiesbelow.poppy.unfiltered_bottle_required")
      )
      return InteractionResult.SUCCESS
    }
    val waterBottleSlot = findWaterBottleSlot(inventory.getNonEquipmentItems)
    if (waterBottleSlot < 0) {
      player.sendOverlayMessage(
        Component.translatable("message.casualtiesbelow.poppy.water_bottle_required")
      )
      return InteractionResult.SUCCESS
    }

    filter.consume(1, player)
    val output = new ItemStack(CasualtiesBelowItems.CrudePoppyLiquid)
    if (player.hasInfiniteMaterials()) {
      if (!inventory.add(output)) player.drop(output, false)
    } else {
      inventory.setItem(bottleSlot, output)
      inventory.setItem(waterBottleSlot, new ItemStack(Items.GLASS_BOTTLE))
    }

    player.awardStat(Stats.ITEM_USED.get(CasualtiesBelowItems.CrudeFilter))
    level.playSound(
      null,
      player.blockPosition(),
      SoundEvents.BOTTLE_FILL,
      SoundSource.PLAYERS,
      1.0f,
      1.0f
    )
    InteractionResult.SUCCESS
  }

  private[item] def isUnfilteredBottleContents(contents: LiquidContents): Boolean =
    contents == LiquidContents.UnfilteredPoppyLiquid

  private def isUnfilteredBottle(stack: ItemStack): Boolean = {
    if (stack.isEmpty || stack.getItem != CasualtiesBelowItems.UnfilteredPoppyLiquid) return false

    val contents = stack.get(CasualtiesBelowDataComponents.LiquidContentsComponent)
    contents != null && isUnfilteredBottleContents(contents)
  }

  private def findUnfilteredBottleSlot(items: List[ItemStack]): Int = {
    var slot = 0
    while (slot < items.size()) {
      if (isUnfilteredBottle(items.get(slot))) return slot
      slot += 1
    }
    -1
  }

  /** Scoops a full infusion cauldron into an unfiltered bucket, draining it back to a vanilla empty
    * cauldron. A partially filled cauldron cannot fill a bucket and is left untouched.
    */
  private[casualtiesbelow] def useBucketScoop(
      player: Player,
      level: Level,
      pos: BlockPos,
      state: BlockState,
      bucket: ItemStack
  ): InteractionResult = {
    if (level.isClientSide()) return InteractionResult.SUCCESS

    val currentLevel = state.getValue[JInteger](LayeredCauldronBlock.LEVEL).intValue()
    if (currentLevel < FullCauldronLevel) {
      player.sendOverlayMessage(
        Component.translatable("message.casualtiesbelow.poppy.full_infusion_required")
      )
      return InteractionResult.SUCCESS
    }

    level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState())
    if (!player.hasInfiniteMaterials()) bucket.consume(1, player)
    val filled = new ItemStack(CasualtiesBelowItems.UnfilteredPoppyLiquidBucket)
    if (!player.getInventory.add(filled)) player.drop(filled, false)

    player.awardStat(Stats.USE_CAULDRON)
    level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f)
    level.gameEvent(player, GameEvent.FLUID_PICKUP, pos)
    InteractionResult.SUCCESS
  }

  /** Pours an unfiltered bucket into an empty cauldron, filling it to a full infusion cauldron. */
  private[casualtiesbelow] def useBucketPour(
      player: Player,
      level: Level,
      pos: BlockPos,
      bucket: ItemStack
  ): InteractionResult = {
    if (level.isClientSide()) return InteractionResult.SUCCESS

    level.setBlockAndUpdate(
      pos,
      CasualtiesBelowBlocks.PoppyInfusionCauldron.defaultBlockState()
    )
    if (!player.hasInfiniteMaterials()) {
      bucket.consume(1, player)
      val empty = new ItemStack(Items.BUCKET)
      if (!player.getInventory.add(empty)) player.drop(empty, false)
    }

    player.awardStat(Stats.USE_CAULDRON)
    level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f)
    level.gameEvent(player, GameEvent.FLUID_PLACE, pos)
    InteractionResult.SUCCESS
  }

  private[item] def isFullWaterCauldron(state: BlockState): Boolean =
    state.is(Blocks.WATER_CAULDRON) &&
      state
        .getValue[JInteger](LayeredCauldronBlock.LEVEL)
        .intValue() == FullCauldronLevel

  private[item] def isWaterBottle(stack: ItemStack): Boolean = {
    if (stack.getItem != Items.POTION) return false

    val contents = stack.get(DataComponents.POTION_CONTENTS)
    contents != null && contents.is(Potions.WATER)
  }

  private[item] def findWaterBottleSlot(items: List[ItemStack]): Int = {
    var slot = 0
    while (slot < items.size()) {
      if (isWaterBottle(items.get(slot))) return slot
      slot += 1
    }
    -1
  }
}
