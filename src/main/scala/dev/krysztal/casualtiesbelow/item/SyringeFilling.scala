package dev.krysztal.casualtiesbelow.item

import java.util.List

import net.minecraft.core.component.DataComponents
import net.minecraft.sounds.SoundEvents
import net.minecraft.stats.Stats
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level

import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extension.Prelude.*

/** Server-authoritative drawing of one liquid dose into an empty syringe. Filled refined ampoules
  * take priority over crude bottles; refined bottles are intentionally not eligible.
  */
private[casualtiesbelow] object SyringeFilling {

  private[item] enum SourceKind {
    case RefinedAmpoule, CrudeBottle
  }

  private[item] final case class Source(slot: Int, kind: SourceKind)

  def interact(
      player: Player,
      level: Level,
      hand: InteractionHand,
      syringe: SyringeItem
  ): InteractionResult = {
    if (player.isSpectator()) return InteractionResult.PASS

    val heldStack = player.getItemInHand(hand)
    if (!syringe.isEmpty(heldStack)) return InteractionResult.PASS
    if (level.isClientSide()) return InteractionResult.SUCCESS

    val inventory = player.getInventory
    findSource(
      inventory.getNonEquipmentItems,
      CasualtiesBelowItems.Ampoule,
      CasualtiesBelowItems.CrudePoppyLiquid
    ) match {
      case None =>
        player.sendOverlayMessage(
          "message.casualtiesbelow.syringe.liquid_required".translatable()
        )
      case Some(source) =>
        val sourceStack = inventory.getItem(source.slot)
        val filled = filledSyringeStack(
          syringe,
          source.kind,
          player.getRandom.nextGaussian(),
          player.getRandom.nextDouble() * 2.0 - 1.0
        )
        heldStack.consume(1, player)
        if (!player.hasInfiniteMaterials()) {
          source.kind match {
            case SourceKind.RefinedAmpoule => sourceStack.consume(1, player)
            case SourceKind.CrudeBottle    =>
              inventory.setItem(
                source.slot,
                crudeSourceAfterDraw(sourceStack, Items.GLASS_BOTTLE)
              )
          }
        }
        if (!inventory.add(filled)) player.drop(filled, false)
        player.awardStat(Stats.ITEM_USED.get(syringe))
        level.playPlayerSound(player, SoundEvents.BOTTLE_FILL, volume = 0.8f, pitch = 1.2f)
    }
    InteractionResult.SUCCESS
  }

  private[item] def findSource(
      items: List[ItemStack],
      ampouleItem: Item,
      crudeBottleItem: Item
  ): Option[Source] = {
    findSlot(items, isFilledRefinedAmpoule(_, ampouleItem))
      .map(Source(_, SourceKind.RefinedAmpoule))
      .orElse(
        findSlot(items, isEligibleCrudeBottle(_, crudeBottleItem))
          .map(Source(_, SourceKind.CrudeBottle))
      )
  }

  private def findSlot(items: List[ItemStack], eligible: ItemStack => Boolean): Option[Int] = {
    var slot = 0
    while (slot < items.size()) {
      if (eligible(items.get(slot))) return Some(slot)
      slot += 1
    }
    None
  }

  private[item] def isFilledRefinedAmpoule(stack: ItemStack, ampouleItem: Item): Boolean = {
    if (stack.isEmpty || stack.getItem != ampouleItem) return false
    stack.liquidContents.contains(LiquidContents.RefinedPoppyAmpoule)
  }

  private[item] def isEligibleCrudeBottle(stack: ItemStack, crudeBottleItem: Item): Boolean = {
    if (stack.isEmpty || stack.getItem != crudeBottleItem) return false
    stack.liquidContents.exists(contents =>
      contents.liquid == LiquidContents.CrudePoppyLiquid.liquid &&
        contents.droplets >= LiquidContents.AmpouleDroplets
    )
  }

  private[item] def filledSyringeStack(
      syringe: SyringeItem,
      sourceKind: SourceKind,
      gaussianSample: Double,
      jitterUnit: Double
  ): ItemStack = {
    val liquid = sourceKind match {
      case SourceKind.RefinedAmpoule => LiquidContents.RefinedPoppyExtract.liquid
      case SourceKind.CrudeBottle    => LiquidContents.CrudePoppyLiquid.liquid
    }
    val baseDose = sampledBaseDose(
      sourceKind,
      gaussianSample,
      CasualtiesBelowConfig.medicineFood.refinedSyringeDose.get(),
      CasualtiesBelowConfig.medicineFood.crudeSyringeDoseMean.get(),
      Consts.Opioid.CrudeSyringeDoseSigma
    )
    val dose = measuredDose(baseDose, syringe.calibrated, jitterUnit)
    val stack = new ItemStack(syringe)
    stack.withSyringeContents(SyringeContents(liquid, LiquidContents.AmpouleDroplets, dose))
    stack.set(DataComponents.MAX_STACK_SIZE, 1)
    stack
  }

  private[item] def sampledBaseDose(
      sourceKind: SourceKind,
      gaussianSample: Double,
      refinedDose: Double,
      crudeMean: Double,
      crudeSigma: Double
  ): Double = sourceKind match {
    case SourceKind.RefinedAmpoule => refinedDose.max(0.0)
    case SourceKind.CrudeBottle    =>
      crudeBaseDose(gaussianSample, crudeMean, crudeSigma)
  }

  /** Crude-poppy base dose: a normal sample clamped to the fixed plausible range. The clamp bounds
    * are recipe constants, not tuning knobs — a 3σ-outlier bottle is a drawing hazard, not a
    * balance axis.
    */
  private[item] def crudeBaseDose(
      gaussianSample: Double,
      mean: Double,
      sigma: Double
  ): Double = {
    val sample = mean.max(0.0) + gaussianSample.finiteOrZero * sigma.max(0.0)
    sample.max(CrudeDoseMinimum).min(CrudeDoseMaximum)
  }

  private[item] def measuredDose(
      baseDose: Double,
      calibrated: Boolean,
      jitterUnit: Double,
      jitterFraction: Double = Consts.Opioid.UnmarkedSyringeJitterFraction
  ): Double = {
    if (calibrated) baseDose.max(0.0)
    else {
      val boundedJitter = jitterUnit.finiteOrZero.max(-1.0).min(1.0)
      baseDose.max(0.0) * (1.0 + boundedJitter * jitterFraction.max(0.0).min(1.0))
    }
  }

  private[item] def crudeSourceAfterDraw(
      source: ItemStack,
      emptyBottleItem: Item
  ): ItemStack = {
    val contents = source.liquidContents.getOrElse(
      throw IllegalArgumentException("source must contain one crude syringe dose")
    )
    require(
      contents.liquid == LiquidContents.CrudePoppyLiquid.liquid &&
        contents.droplets >= LiquidContents.AmpouleDroplets,
      "source must contain one crude syringe dose"
    )
    val remaining = contents.droplets - LiquidContents.AmpouleDroplets
    if (remaining == 0L) new ItemStack(emptyBottleItem)
    else {
      val updated = source.copy()
      updated.withLiquidContents(contents.copy(droplets = remaining))
      updated
    }
  }

  // Fixed plausible range of one crude-poppy syringe dose (see crudeBaseDose).
  private val CrudeDoseMinimum = 20.0
  private val CrudeDoseMaximum = 60.0
}
