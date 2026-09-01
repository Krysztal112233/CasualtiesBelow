package dev.krysztal.casualtiesbelow.item

import java.lang.Boolean as JBoolean

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.ShelfBlock
import net.minecraft.world.level.block.entity.ShelfBlockEntity
import net.minecraft.world.level.block.state.BlockState

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowTags

/** Server-side drying behavior shared by every vanilla shelf wood variant. */
private[casualtiesbelow] object ShelfDrying {
  private[item] val RequiredAdvances = 3
  private[item] val SuccessChance = 0.70f

  def randomTick(
      state: BlockState,
      level: ServerLevel,
      pos: BlockPos,
      random: RandomSource
  ): Unit = {
    if (state.getValue[JBoolean](ShelfBlock.WATERLOGGED).booleanValue()) return

    level.getBlockEntity(pos) match {
      case shelf: ShelfBlockEntity => advanceSelectedSlot(shelf, random)
      case _                       => ()
    }
  }

  private def advanceSelectedSlot(shelf: ShelfBlockEntity, random: RandomSource): Unit = {
    val slot = random.nextInt(ShelfBlockEntity.MAX_ITEMS)
    val stack = shelf.getItem(slot)
    if (stack.isEmpty || !stack.is(CasualtiesBelowTags.DriesToFiberClothItems)) return
    if (!shouldAdvance(random.nextFloat())) return

    val nextStage = advanceStage(stageOf(stack))
    if (nextStage >= RequiredAdvances) {
      shelf.setItemNoUpdate(slot, convertedStack(stack, CasualtiesBelowItems.FiberCloth))
    } else {
      applyInProgressStage(stack, nextStage)
    }

    // Persist and synchronize the changed stack without ShelfBlockEntity's BLOCK_ACTIVATE event.
    shelf.setChanged(null)
  }

  private[item] def applyInProgressStage(stack: ItemStack, stage: Int): Unit = {
    require(stage > 0 && stage < RequiredAdvances, s"invalid in-progress drying stage: $stage")
    stack.set(
      CasualtiesBelowDataComponents.DryingStageComponent,
      DryingStage(stage)
    )
  }

  private[item] def stageOf(stack: ItemStack): Int = {
    val stage = stack.get(CasualtiesBelowDataComponents.DryingStageComponent)
    if (stage == null) 0 else stage.value
  }

  private[item] def shouldAdvance(roll: Float): Boolean = roll < SuccessChance

  private[item] def advanceStage(currentStage: Int): Int = currentStage + 1

  private[item] def convertedStack(input: ItemStack, output: Item): ItemStack =
    new ItemStack(output, input.getCount)
}
