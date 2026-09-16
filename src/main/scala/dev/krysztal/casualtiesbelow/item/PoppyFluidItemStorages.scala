package dev.krysztal.casualtiesbelow.item

import java.lang.Integer as JInteger
import java.util.concurrent.ThreadLocalRandom

import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext

import dev.krysztal.casualtiesbelow.fluid.PoppyFluids

/** Amount arithmetic for the item-bound fluid storages, split from registry-bound items so
  * frozen-registry unit tests can exercise the clamping rules directly.
  */
private[item] object ContainerFluidMath {

  def clampedInsert(amount: Long, capacity: Long, maxAmount: Long): Long =
    if (maxAmount <= 0L || amount >= capacity) 0L
    else Math.min(maxAmount, capacity - amount)

  def clampedExtract(amount: Long, maxAmount: Long): Long =
    if (maxAmount <= 0L || amount <= 0L) 0L
    else Math.min(maxAmount, amount)

  /** A syringe dose must be drawn in one motion: inserts below one full dose move nothing. */
  def syringeDrawAmount(maxAmount: Long, doseDroplets: Long): Long =
    if (maxAmount >= doseDroplets) doseDroplets else 0L
}

/** Static description of one liquid-container item family: which fluid it holds, its capacity in
  * droplets, how to read the held amount from an item variant, and which variants represent the
  * filled and drained states.
  */
private[item] final class ContainerFluidSpec(
    val acceptedFluids: Seq[net.minecraft.world.level.material.Fluid],
    val capacity: Long,
    val readAmount: ItemVariant => Option[Long],
    val readFluid: ItemVariant => Option[net.minecraft.world.level.material.Fluid],
    val fillTarget: (ItemVariant, net.minecraft.world.level.material.Fluid, Long) => ItemVariant,
    val drainTarget: ItemVariant,
    val singleDrawDose: Boolean
)

/** A single-slot Storage view over a held liquid container item. All mutations go through
  * ContainerItemContext.exchange inside the caller's transaction, so simulation and rollback come
  * from the Transfer API for free. Fluid variants stay component-free: identity is the registered
  * Fluid, amounts live in the item's own components, matching ecosystem conventions for machine
  * interoperability.
  */
private[item] final class ContainerFluidStorage(
    context: ContainerItemContext,
    spec: ContainerFluidSpec
) extends SingleSlotStorage[FluidVariant] {

  override def getResource(): FluidVariant =
    spec.readFluid(currentVariant()).map(FluidVariant.of).getOrElse(FluidVariant.blank())

  override def getAmount(): Long = spec.readAmount(currentVariant()).getOrElse(0L)

  override def getCapacity(): Long = spec.capacity

  override def isResourceBlank(): Boolean = getAmount() <= 0L

  override def supportsInsertion(): Boolean = true

  override def supportsExtraction(): Boolean = getAmount() > 0L

  override def insert(
      resource: FluidVariant,
      maxAmount: Long,
      transaction: TransactionContext
  ): Long = {
    if (maxAmount <= 0L || resource.isBlank()) return 0L
    val fluid = resource.getFluid()
    if (!spec.acceptedFluids.contains(fluid)) return 0L

    spec.readAmount(currentVariant()) match {
      case Some(current) =>
        // Top-up of a partially drained container; only the held liquid is accepted.
        if (spec.readFluid(currentVariant()) != Some(fluid)) return 0L
        val moved = ContainerFluidMath.clampedInsert(current, spec.capacity, maxAmount)
        if (moved > 0L)
          context.exchange(
            spec.fillTarget(currentVariant(), fluid, current + moved),
            1L,
            transaction
          )
        moved
      case None =>
        val requested =
          if (spec.singleDrawDose) ContainerFluidMath.syringeDrawAmount(maxAmount, spec.capacity)
          else Math.min(maxAmount, spec.capacity)
        if (requested <= 0L) return 0L
        context.exchange(spec.fillTarget(currentVariant(), fluid, requested), 1L, transaction)
        requested
    }
  }

  override def extract(
      resource: FluidVariant,
      maxAmount: Long,
      transaction: TransactionContext
  ): Long = {
    if (maxAmount <= 0L || resource.isBlank()) return 0L
    val fluid = resource.getFluid()
    if (!spec.acceptedFluids.contains(fluid)) return 0L

    spec.readAmount(currentVariant()) match {
      case Some(current) if spec.readFluid(currentVariant()) == Some(fluid) =>
        val moved = ContainerFluidMath.clampedExtract(current, maxAmount)
        if (moved <= 0L) return 0L
        val target =
          if (moved == current) spec.drainTarget
          else spec.fillTarget(currentVariant(), fluid, current - moved)
        context.exchange(target, 1L, transaction)
        moved
      case _ => 0L
    }
  }

  private def currentVariant(): ItemVariant = context.getItemVariant()
}

/** Registers FluidStorage.ITEM providers for the poppy liquid containers: the three bottles, the
  * ampoule, and both syringes. Only the mod's own fluids are accepted; third-party fluids can be
  * carried by the mod's buckets (vanilla bucket mapping) but never enter bottles, ampoules or
  * syringes.
  */
private[casualtiesbelow] object PoppyFluidItemStorages {

  def register(): Unit = {
    registerBottle(
      CasualtiesBelowItems.UnfilteredPoppyLiquid,
      PoppyFluids.UnfilteredPoppyLiquid,
      FluidConstants.BOTTLE
    )
    registerBottle(
      CasualtiesBelowItems.CrudePoppyLiquid,
      PoppyFluids.CrudePoppyLiquid,
      FluidConstants.BOTTLE
    )
    registerBottle(
      CasualtiesBelowItems.RefinedPoppyExtract,
      PoppyFluids.RefinedPoppyExtract,
      LiquidContents.RefinedPoppyExtract.droplets
    )

    val ampouleSpec = new ContainerFluidSpec(
      acceptedFluids = Seq(PoppyFluids.RefinedPoppyExtract),
      capacity = LiquidContents.AmpouleDroplets,
      readAmount = variant =>
        Option(
          variant.get(CasualtiesBelowDataComponents.LiquidContentsComponent)
        ).filter(_.liquid == LiquidContents.RefinedPoppyExtract.liquid).map(_.droplets),
      readFluid = variant =>
        Option(
          variant.get(CasualtiesBelowDataComponents.LiquidContentsComponent)
        ).filter(_.liquid == LiquidContents.RefinedPoppyExtract.liquid)
          .map(_ => PoppyFluids.RefinedPoppyExtract),
      fillTarget = (_, _, amount) =>
        ItemVariant.of(
          CasualtiesBelowItems.Ampoule,
          liquidPatch(LiquidContents(LiquidContents.RefinedPoppyExtract.liquid, amount))
        ),
      drainTarget = ItemVariant.of(CasualtiesBelowItems.Ampoule),
      singleDrawDose = false
    )
    FluidStorage.ITEM.registerForItems(
      (_, context) => new ContainerFluidStorage(context, ampouleSpec),
      CasualtiesBelowItems.Ampoule
    )

    FluidStorage.ITEM.registerForItems(
      (stack, context) =>
        new ContainerFluidStorage(context, syringeSpec(stack.getItem.asInstanceOf[SyringeItem])),
      CasualtiesBelowItems.UnmarkedSyringe,
      CasualtiesBelowItems.CalibratedSyringe
    )
  }

  private def registerBottle(
      item: Item,
      fluid: net.minecraft.world.level.material.Fluid,
      capacity: Long
  ): Unit = {
    val spec = new ContainerFluidSpec(
      acceptedFluids = Seq(fluid),
      capacity = capacity,
      readAmount = variant =>
        Option(variant.get(CasualtiesBelowDataComponents.LiquidContentsComponent)).map(_.droplets),
      readFluid = _ => Some(fluid),
      fillTarget = (_, _, amount) =>
        ItemVariant.of(item, liquidPatch(LiquidContents(fluidIdFor(fluid), amount))),
      drainTarget = ItemVariant.of(Items.GLASS_BOTTLE),
      singleDrawDose = false
    )
    FluidStorage.ITEM.registerForItems(
      (_, context) => new ContainerFluidStorage(context, spec),
      item
    )
  }

  private def syringeSpec(syringe: SyringeItem): ContainerFluidSpec =
    new ContainerFluidSpec(
      acceptedFluids = Seq(PoppyFluids.CrudePoppyLiquid, PoppyFluids.RefinedPoppyExtract),
      capacity = LiquidContents.AmpouleDroplets,
      readAmount = variant =>
        Option(variant.get(CasualtiesBelowDataComponents.SyringeContentsComponent)).map(_.droplets),
      readFluid = variant =>
        Option(
          variant.get(CasualtiesBelowDataComponents.SyringeContentsComponent)
        ).flatMap(contents => fluidForId(contents.liquid)),
      fillTarget = (currentVariant, fluid, amount) => {
        Option(
          currentVariant.get(CasualtiesBelowDataComponents.SyringeContentsComponent)
        ) match {
          case Some(contents) =>
            // Top-up keeps the originally sampled dose; settlement scales it by droplets.
            ItemVariant.of(
              syringe,
              syringePatch(SyringeContents(contents.liquid, amount, contents.opioidDose))
            )
          case None =>
            val kind =
              if (fluid == PoppyFluids.CrudePoppyLiquid) SyringeFilling.SourceKind.CrudeBottle
              else SyringeFilling.SourceKind.RefinedAmpoule
            val random = ThreadLocalRandom.current()
            ItemVariant.of(
              SyringeFilling.filledSyringeStack(
                syringe,
                kind,
                random.nextGaussian(),
                random.nextDouble() * 2.0 - 1.0
              )
            )
        }
      },
      drainTarget = ItemVariant.of(syringe),
      singleDrawDose = true
    )

  private def liquidPatch(contents: LiquidContents): DataComponentPatch =
    DataComponentPatch
      .builder()
      .set(CasualtiesBelowDataComponents.LiquidContentsComponent, contents)
      .build()

  private def syringePatch(contents: SyringeContents): DataComponentPatch =
    DataComponentPatch
      .builder()
      .set(CasualtiesBelowDataComponents.SyringeContentsComponent, contents)
      .set(DataComponents.MAX_STACK_SIZE, JInteger.valueOf(1))
      .build()

  private def fluidIdFor(fluid: net.minecraft.world.level.material.Fluid): Identifier =
    fluid match {
      case PoppyFluids.UnfilteredPoppyLiquid => LiquidContents.UnfilteredPoppyLiquid.liquid
      case PoppyFluids.CrudePoppyLiquid      => LiquidContents.CrudePoppyLiquid.liquid
      case _                                 => LiquidContents.RefinedPoppyExtract.liquid
    }

  private def fluidForId(liquid: Identifier): Option[net.minecraft.world.level.material.Fluid] =
    liquid match {
      case LiquidContents.CrudePoppyLiquid.liquid    => Some(PoppyFluids.CrudePoppyLiquid)
      case LiquidContents.RefinedPoppyExtract.liquid => Some(PoppyFluids.RefinedPoppyExtract)
      case _                                         => None
    }
}
