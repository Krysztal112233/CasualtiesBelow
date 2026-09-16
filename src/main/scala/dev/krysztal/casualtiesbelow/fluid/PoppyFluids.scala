package dev.krysztal.casualtiesbelow.fluid

import java.lang.Integer as JInteger
import java.util.Optional

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.Item
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.material.FlowingFluid
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FluidState

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi
import dev.krysztal.casualtiesbelow.block.CasualtiesBelowBlocks
import dev.krysztal.casualtiesbelow.item.CasualtiesBelowItems

/** Shared behavior for the mod's poppy liquids. Spreading is water-like, but sources never
  * regenerate (the source-conversion gate is permanently closed, unlike water's gamerule) so fluid
  * automation can never duplicate a liquid: every droplet still originates from the poppy
  * processing chain and its refining losses.
  */
private[fluid] abstract class PoppyFluidBase extends FlowingFluid {

  override protected def canConvertToSource(level: ServerLevel): Boolean = false

  override protected def beforeDestroyingBlock(
      level: LevelAccessor,
      pos: BlockPos,
      state: BlockState
  ): Unit =
    if (state.hasBlockEntity) {
      Block.dropResources(state, level, pos, level.getBlockEntity(pos))
    } else {
      Block.dropResources(state, level, pos, null)
    }

  override protected def getSlopeFindDistance(level: LevelReader): Int = 4

  override protected def getDropOff(level: LevelReader): Int = 1

  override def getTickDelay(level: LevelReader): Int = 5

  override protected def canBeReplacedWith(
      state: FluidState,
      level: BlockGetter,
      pos: BlockPos,
      other: Fluid,
      direction: Direction
  ): Boolean =
    direction == Direction.DOWN && !other.isSame(this)

  override protected def getExplosionResistance(): Float = 100.0f

  override def getPickupSound(): Optional[SoundEvent] = Optional.of(SoundEvents.BUCKET_FILL)

  /** Vanilla's protected static helper reimplemented to avoid cross-object static access. */
  protected final def legacyLevel(state: FluidState): Int =
    if (state.isSource) 0
    else
      8 - Math.min(state.getAmount, 8) + (if (state.getValue(FlowingFluid.FALLING).booleanValue()) 8
                                          else 0)
}

/** Non-source side of a still/flowing pair; owns the flowing LEVEL property. */
private[fluid] abstract class PoppyFlowingFluid extends PoppyFluidBase {

  override protected def createFluidStateDefinition(
      builder: StateDefinition.Builder[Fluid, FluidState]
  ): Unit = {
    super.createFluidStateDefinition(builder)
    builder.add(FlowingFluid.LEVEL)
  }

  override def getAmount(state: FluidState): Int =
    state.getValue[JInteger](FlowingFluid.LEVEL).intValue()

  override def isSource(state: FluidState): Boolean = false
}

/** Source side of a still/flowing pair. */
private[fluid] abstract class PoppySourceFluid extends PoppyFluidBase {

  override def getAmount(state: FluidState): Int = 8

  override def isSource(state: FluidState): Boolean = true
}

/** Registry entries for the three poppy liquids as vanilla-style fluid pairs. Must initialize
  * before blocks and items: the fluid blocks take the source fluid in their constructor, and the
  * buckets take it in theirs. Identity, bucket and legacy-block wiring lives in the anonymous
  * subclasses because each side of a pair references its sibling.
  */
object PoppyFluids {
  private val UnfilteredKey: ResourceKey[Fluid] =
    ResourceKey.create(Registries.FLUID, CasualtiesBelowApi.id("unfiltered_poppy_liquid"))
  private val FlowingUnfilteredKey: ResourceKey[Fluid] =
    ResourceKey.create(Registries.FLUID, CasualtiesBelowApi.id("flowing_unfiltered_poppy_liquid"))
  private val CrudeKey: ResourceKey[Fluid] =
    ResourceKey.create(Registries.FLUID, CasualtiesBelowApi.id("crude_poppy_liquid"))
  private val FlowingCrudeKey: ResourceKey[Fluid] =
    ResourceKey.create(Registries.FLUID, CasualtiesBelowApi.id("flowing_crude_poppy_liquid"))
  private val RefinedKey: ResourceKey[Fluid] =
    ResourceKey.create(Registries.FLUID, CasualtiesBelowApi.id("refined_poppy_extract"))
  private val FlowingRefinedKey: ResourceKey[Fluid] =
    ResourceKey.create(Registries.FLUID, CasualtiesBelowApi.id("flowing_refined_poppy_extract"))

  val UnfilteredPoppyLiquid: PoppySourceFluid = Registry.register(
    BuiltInRegistries.FLUID,
    UnfilteredKey,
    new PoppySourceFluid {
      override def getFlowing(): Fluid = FlowingUnfilteredPoppyLiquid
      override def getSource(): Fluid = UnfilteredPoppyLiquid
      override def isSame(other: Fluid): Boolean =
        other == UnfilteredPoppyLiquid || other == FlowingUnfilteredPoppyLiquid
      override protected def createLegacyBlock(state: FluidState): BlockState =
        CasualtiesBelowBlocks.UnfilteredPoppyLiquid
          .defaultBlockState()
          .setValue[JInteger, JInteger](
            LiquidBlock.LEVEL,
            JInteger.valueOf(legacyLevel(state))
          )
      override def getBucket(): Item = CasualtiesBelowItems.UnfilteredPoppyLiquidBucket
    }
  )

  val FlowingUnfilteredPoppyLiquid: PoppyFlowingFluid = Registry.register(
    BuiltInRegistries.FLUID,
    FlowingUnfilteredKey,
    new PoppyFlowingFluid {
      override def getFlowing(): Fluid = FlowingUnfilteredPoppyLiquid
      override def getSource(): Fluid = UnfilteredPoppyLiquid
      override def isSame(other: Fluid): Boolean =
        other == UnfilteredPoppyLiquid || other == FlowingUnfilteredPoppyLiquid
      override protected def createLegacyBlock(state: FluidState): BlockState =
        CasualtiesBelowBlocks.UnfilteredPoppyLiquid
          .defaultBlockState()
          .setValue[JInteger, JInteger](
            LiquidBlock.LEVEL,
            JInteger.valueOf(legacyLevel(state))
          )
      override def getBucket(): Item = CasualtiesBelowItems.UnfilteredPoppyLiquidBucket
    }
  )

  val CrudePoppyLiquid: PoppySourceFluid = Registry.register(
    BuiltInRegistries.FLUID,
    CrudeKey,
    new PoppySourceFluid {
      override def getFlowing(): Fluid = FlowingCrudePoppyLiquid
      override def getSource(): Fluid = CrudePoppyLiquid
      override def isSame(other: Fluid): Boolean =
        other == CrudePoppyLiquid || other == FlowingCrudePoppyLiquid
      override protected def createLegacyBlock(state: FluidState): BlockState =
        CasualtiesBelowBlocks.CrudePoppyLiquid
          .defaultBlockState()
          .setValue[JInteger, JInteger](
            LiquidBlock.LEVEL,
            JInteger.valueOf(legacyLevel(state))
          )
      override def getBucket(): Item = CasualtiesBelowItems.CrudePoppyLiquidBucket
    }
  )

  val FlowingCrudePoppyLiquid: PoppyFlowingFluid = Registry.register(
    BuiltInRegistries.FLUID,
    FlowingCrudeKey,
    new PoppyFlowingFluid {
      override def getFlowing(): Fluid = FlowingCrudePoppyLiquid
      override def getSource(): Fluid = CrudePoppyLiquid
      override def isSame(other: Fluid): Boolean =
        other == CrudePoppyLiquid || other == FlowingCrudePoppyLiquid
      override protected def createLegacyBlock(state: FluidState): BlockState =
        CasualtiesBelowBlocks.CrudePoppyLiquid
          .defaultBlockState()
          .setValue[JInteger, JInteger](
            LiquidBlock.LEVEL,
            JInteger.valueOf(legacyLevel(state))
          )
      override def getBucket(): Item = CasualtiesBelowItems.CrudePoppyLiquidBucket
    }
  )

  val RefinedPoppyExtract: PoppySourceFluid = Registry.register(
    BuiltInRegistries.FLUID,
    RefinedKey,
    new PoppySourceFluid {
      override def getFlowing(): Fluid = FlowingRefinedPoppyExtract
      override def getSource(): Fluid = RefinedPoppyExtract
      override def isSame(other: Fluid): Boolean =
        other == RefinedPoppyExtract || other == FlowingRefinedPoppyExtract
      override protected def createLegacyBlock(state: FluidState): BlockState =
        CasualtiesBelowBlocks.RefinedPoppyExtract
          .defaultBlockState()
          .setValue[JInteger, JInteger](
            LiquidBlock.LEVEL,
            JInteger.valueOf(legacyLevel(state))
          )
      override def getBucket(): Item = CasualtiesBelowItems.RefinedPoppyExtractBucket
    }
  )

  val FlowingRefinedPoppyExtract: PoppyFlowingFluid = Registry.register(
    BuiltInRegistries.FLUID,
    FlowingRefinedKey,
    new PoppyFlowingFluid {
      override def getFlowing(): Fluid = FlowingRefinedPoppyExtract
      override def getSource(): Fluid = RefinedPoppyExtract
      override def isSame(other: Fluid): Boolean =
        other == RefinedPoppyExtract || other == FlowingRefinedPoppyExtract
      override protected def createLegacyBlock(state: FluidState): BlockState =
        CasualtiesBelowBlocks.RefinedPoppyExtract
          .defaultBlockState()
          .setValue[JInteger, JInteger](
            LiquidBlock.LEVEL,
            JInteger.valueOf(legacyLevel(state))
          )
      override def getBucket(): Item = CasualtiesBelowItems.RefinedPoppyExtractBucket
    }
  )

  /** Forces eager registry-field initialization; must run before blocks and items. */
  def register(): Unit = ()
}
