package dev.krysztal.casualtiesbelow.mixin

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.InsideBlockEffectApplier
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LayeredCauldronBlock
import net.minecraft.world.level.block.state.BlockState

import dev.krysztal.casualtiesbelow.item.PoppyProcessing

import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Detects paste entities entering vanilla layered cauldrons without replacing their behavior. */
@Mixin(value = Array(classOf[LayeredCauldronBlock]), remap = false)
abstract class LayeredCauldronBlockMixin {

  @Inject(method = Array("entityInside"), at = Array(new At(value = "HEAD")), remap = false)
  private def casualtiesbelow$startPoppySoak(
      state: BlockState,
      level: Level,
      pos: BlockPos,
      entity: Entity,
      effectApplier: InsideBlockEffectApplier,
      isPrecise: Boolean,
      ci: CallbackInfo
  ): Unit = {
    if (!state.is(Blocks.WATER_CAULDRON)) return

    entity match {
      case itemEntity: ItemEntity =>
        PoppyProcessing.tryStartSoakFromDroppedItem(level, pos, itemEntity)
      case _ =>
    }
  }
}
