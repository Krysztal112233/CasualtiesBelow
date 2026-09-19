package dev.krysztal.casualtiesbelow.mixin

import net.minecraft.world.food.FoodData

import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

/** Exposes the accumulated exhaustion level; the body-temperature system tracks its per-tick deltas
  * as the exercise heat signal.
  */
@Mixin(value = Array(classOf[FoodData]), remap = false)
trait FoodDataAccessor {
  @Accessor(value = "exhaustionLevel", remap = false)
  def casualtiesbelow$getExhaustionLevel(): Float
}
