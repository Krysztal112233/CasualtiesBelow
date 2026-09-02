package dev.krysztal.casualtiesbelow.block

import java.lang.Integer as JInteger

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.cauldron.CauldronInteractions
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.AbstractCauldronBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.LayeredCauldronBlock
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.phys.BlockHitResult

import dev.krysztal.casualtiesbelow.block.entity.SoakingPoppyCauldronBlockEntity
import dev.krysztal.casualtiesbelow.item.PoppyProcessing

/** Cauldron-shaped block carrying three bottle-sized levels of poppy infusion. */
private abstract class PoppyCauldronBlock(properties: BlockBehaviour.Properties)
    extends AbstractCauldronBlock(properties, CauldronInteractions.EMPTY) {

  registerDefaultState(
    stateDefinition
      .any()
      .setValue[JInteger, JInteger](
        PoppyCauldronBlock.Level,
        JInteger.valueOf(PoppyProcessing.FullCauldronLevel)
      )
  )

  override def isFull(state: BlockState): Boolean =
    state.getValue[JInteger](PoppyCauldronBlock.Level).intValue() ==
      PoppyProcessing.FullCauldronLevel

  // CauldronInteractions.EMPTY still accepts buckets and water bottles; bypass it so those
  // vanilla interactions cannot overwrite an active poppy batch.
  override protected def useItemOn(
      stack: ItemStack,
      state: BlockState,
      level: Level,
      pos: BlockPos,
      player: Player,
      hand: InteractionHand,
      hitResult: BlockHitResult
  ): InteractionResult = InteractionResult.TRY_WITH_EMPTY_HAND

  override protected def getContentHeight(state: BlockState): Double =
    (6.0 + state.getValue[JInteger](PoppyCauldronBlock.Level).intValue() * 3.0) / 16.0

  override protected def getAnalogOutputSignal(
      state: BlockState,
      level: Level,
      pos: BlockPos,
      direction: Direction
  ): Int = state.getValue[JInteger](PoppyCauldronBlock.Level).intValue()

  override protected def createBlockStateDefinition(
      builder: StateDefinition.Builder[Block, BlockState]
  ): Unit = builder.add(PoppyCauldronBlock.Level)
}

/** Full cauldron while the paste soaks. Its scheduled tick survives chunk saves and reloads. */
final class SoakingPoppyCauldronBlock(properties: BlockBehaviour.Properties)
    extends PoppyCauldronBlock(properties)
    with EntityBlock {

  override protected def codec(): MapCodec[? <: AbstractCauldronBlock] =
    SoakingPoppyCauldronBlock.Codec

  override def newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    SoakingPoppyCauldronBlockEntity(pos, state)

  override protected def onPlace(
      state: BlockState,
      level: Level,
      pos: BlockPos,
      oldState: BlockState,
      movedByPiston: Boolean
  ): Unit = {
    super.onPlace(state, level, pos, oldState, movedByPiston)
    if (!level.isClientSide() && !oldState.is(this)) {
      val serverLevel = level.asInstanceOf[ServerLevel]
      // Some command-driven replacements skip removal callbacks; clear any same-position
      // stale timer here as well before scheduling the new batch.
      serverLevel.getBlockTicks.clearArea(new BoundingBox(pos))
      serverLevel.scheduleTick(pos, this, PoppyProcessing.SoakDurationTicks)
    }
  }

  override protected def affectNeighborsAfterRemoval(
      state: BlockState,
      level: ServerLevel,
      pos: BlockPos,
      movedByPiston: Boolean
  ): Unit = {
    // A scheduled tick is position-bound and could otherwise complete a later batch placed here.
    level.getBlockTicks.clearArea(new BoundingBox(pos))
    super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston)
  }

  override protected def tick(
      state: BlockState,
      level: ServerLevel,
      pos: BlockPos,
      random: RandomSource
  ): Unit = {
    val readyState = CasualtiesBelowBlocks.PoppyInfusionCauldron
      .defaultBlockState()
      .setValue[JInteger, JInteger](
        PoppyCauldronBlock.Level,
        JInteger.valueOf(PoppyProcessing.FullCauldronLevel)
      )
    level.setBlockAndUpdate(pos, readyState)
    level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos)
    // Reuse the vanilla water-drip cue to signal that the infusion is ready.
    level.levelEvent(LevelEvent.SOUND_DRIP_WATER_INTO_CAULDRON, pos, 0)
  }
}

object SoakingPoppyCauldronBlock {
  val Codec: MapCodec[SoakingPoppyCauldronBlock] =
    BlockBehaviour.simpleCodec(properties => SoakingPoppyCauldronBlock(properties))
}

/** Ready infusion. Each successful filter use removes one of its three levels. */
final class PoppyInfusionCauldronBlock(properties: BlockBehaviour.Properties)
    extends PoppyCauldronBlock(properties) {

  override protected def codec(): MapCodec[? <: AbstractCauldronBlock] =
    PoppyInfusionCauldronBlock.Codec
}

object PoppyInfusionCauldronBlock {
  val Codec: MapCodec[PoppyInfusionCauldronBlock] =
    BlockBehaviour.simpleCodec(properties => PoppyInfusionCauldronBlock(properties))
}

private object PoppyCauldronBlock {
  val Level: IntegerProperty = LayeredCauldronBlock.LEVEL.nn
}
