package dev.krysztal.casualtiesbelow.internal.extensions

import net.minecraft.world.food.FoodData

import dev.krysztal.casualtiesbelow.mixin.FoodDataAccessor

/** Enrichments over [FoodData] for this mod's exhaustion reads. */
private[casualtiesbelow] object FoodDataExtensions {

  extension (foodData: FoodData) {

    /** The accumulated vanilla exhaustion level; its per-tick deltas are the exercise signal. */
    def exhaustionLevel: Float =
      foodData.asInstanceOf[FoodDataAccessor].casualtiesbelow$getExhaustionLevel()
  }
}
