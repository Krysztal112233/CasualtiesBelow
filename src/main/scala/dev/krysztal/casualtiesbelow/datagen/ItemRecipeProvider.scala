package dev.krysztal.casualtiesbelow.datagen

import java.util.concurrent.CompletableFuture

import net.minecraft.core.HolderLookup
import net.minecraft.data.recipes.RecipeCategory
import net.minecraft.data.recipes.RecipeOutput
import net.minecraft.data.recipes.RecipeProvider
import net.minecraft.tags.ItemTags
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

        shaped(RecipeCategory.MISC, CasualtiesBelowItems.CrudeFilter)
          .define('P', ItemTags.PLANKS)
          .define('S', ItemTags.SAND)
          .define('C', Items.CHARCOAL)
          .pattern("PPP")
          .pattern("SSS")
          .pattern("CCC")
          .unlockedBy("has_charcoal", has(Items.CHARCOAL))
          .save(recipeOutput)

        stonecutterResultFromBase(
          RecipeCategory.MISC,
          CasualtiesBelowItems.Ampoule,
          Items.GLASS_PANE,
          2
        )

        shaped(RecipeCategory.MISC, CasualtiesBelowItems.UnmarkedSyringe)
          .define('P', Items.PISTON)
          .define('G', Items.GLASS)
          .define('N', Items.IRON_NUGGET)
          .pattern("P")
          .pattern("G")
          .pattern("N")
          .unlockedBy("has_ampoule", has(CasualtiesBelowItems.Ampoule))
          .save(recipeOutput)

        shapeless(RecipeCategory.MISC, CasualtiesBelowItems.CalibratedSyringe)
          .requires(CasualtiesBelowItems.UnmarkedSyringe)
          .requires(Items.FLINT)
          .unlockedBy("has_unmarked_syringe", has(CasualtiesBelowItems.UnmarkedSyringe))
          .save(recipeOutput)

        shapeless(RecipeCategory.MISC, CasualtiesBelowItems.CrudePoppyPaste)
          .requires(Items.POPPY)
          .requires(Items.CHARCOAL)
          .requires(ItemTags.SAND)
          .unlockedBy("has_poppy", has(Items.POPPY))
          .save(recipeOutput)
      }
    }
  }

  override def getName(): String = "Casualties: Below item recipes"
}
