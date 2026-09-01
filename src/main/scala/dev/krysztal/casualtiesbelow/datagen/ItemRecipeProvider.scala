package dev.krysztal.casualtiesbelow.datagen

import java.util.concurrent.CompletableFuture

import net.minecraft.core.HolderLookup
import net.minecraft.data.recipes.RecipeCategory
import net.minecraft.data.recipes.RecipeOutput
import net.minecraft.data.recipes.RecipeProvider
import net.minecraft.world.item.Items

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider

import dev.krysztal.casualtiesbelow.item.CasualtiesBelowItems

/** Generates recipes for the mod's ordinary items. */
final class ItemRecipeProvider(
    output: FabricPackOutput,
    registries: CompletableFuture[HolderLookup.Provider]
) extends FabricRecipeProvider(output, registries) {

  override protected def createRecipeProvider(
      registries: HolderLookup.Provider,
      recipeOutput: RecipeOutput
  ): RecipeProvider = {
    new RecipeProvider(registries, recipeOutput) {
      override def buildRecipes(): Unit = {
        shaped(RecipeCategory.COMBAT, CasualtiesBelowItems.BasicBandage)
          .define('#', CasualtiesBelowItems.FiberCloth)
          .define('S', Items.STICK)
          .pattern("###")
          .pattern("#S#")
          .pattern("###")
          .unlockedBy("has_fiber_cloth", has(CasualtiesBelowItems.FiberCloth))
          .save(recipeOutput)
      }
    }
  }

  override def getName(): String = "Casualties: Below item recipes"
}
