package dev.krysztal.casualtiesbelow.internal.extension

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.Potions

import dev.krysztal.casualtiesbelow.item.CasualtiesBelowDataComponents
import dev.krysztal.casualtiesbelow.item.DryingStage

/** Enrichments over [ItemStack] shared by mod-internal call sites.
  *
  * Extension methods resolve statically and compile to plain JVM methods, so they add no wrapper
  * allocation. Real ItemStack members always win over same-named extensions, so these names stay
  * distinctive and mod-specific. Import narrowly at the use site (`import
  * ...ItemStackExtensions.*`).
  */
private[casualtiesbelow] object ItemStackExtensions {

  extension (stack: ItemStack) {

    /** Stored in-progress drying stage; a fresh stack (missing component) reads as 0. */
    def dryingStage: Int = {
      val stage = stack.get(CasualtiesBelowDataComponents.DryingStageComponent)
      if (stage == null) 0 else stage.value
    }

    /** Mutates the stack's in-progress drying stage, rejecting non-persisted values. */
    def withDryingStage(stage: Int): Unit = stack.set(
      CasualtiesBelowDataComponents.DryingStageComponent,
      DryingStage.inProgress(stage)
    )

    /** True only for a vanilla potion bottle filled with plain water. */
    def isWaterBottle: Boolean = {
      if (stack.getItem != Items.POTION) return false

      val contents = stack.get(DataComponents.POTION_CONTENTS)
      contents != null && contents.is(Potions.WATER)
    }
  }
}
