package dev.krysztal.casualtiesbelow.datagen

import java.lang.Integer as JInteger

import net.minecraft.client.data.models.BlockModelGenerators
import net.minecraft.client.data.models.ItemModelGenerators
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator
import net.minecraft.client.data.models.blockstates.PropertyDispatch
import net.minecraft.client.data.models.model.ItemModelUtils
import net.minecraft.client.data.models.model.ModelTemplates
import net.minecraft.client.data.models.model.TextureMapping
import net.minecraft.client.resources.model.sprite.Material
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.LayeredCauldronBlock

import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi
import dev.krysztal.casualtiesbelow.block.CasualtiesBelowBlocks
import dev.krysztal.casualtiesbelow.item.CasualtiesBelowItems

/** Generates every model asset for the mod's items and cauldron blocks from vanilla templates,
  * replacing the previously hand-written items/, models/item/, models/block/ and blockstates/ JSON
  * files. Run via `./gradlew runDatagen`; output lands in `src/main/generated/assets` and is
  * packaged by Loom.
  */
final class AssetModelProvider(output: FabricPackOutput) extends FabricModelProvider(output) {

  override def generateItemModels(generator: ItemModelGenerators): Unit = {
    // Plain items rendered with their own texture.
    Seq(
      CasualtiesBelowItems.Ampoule,
      CasualtiesBelowItems.BasicBandage,
      CasualtiesBelowItems.UnmarkedSyringe,
      CasualtiesBelowItems.CalibratedSyringe
    ).foreach(item => generator.generateFlatItem(item, ModelTemplates.FLAT_ITEM))

    // Placeholder items borrowing vanilla sprites.
    plainVanillaItem(generator, CasualtiesBelowItems.FiberCloth, "paper")
    plainVanillaItem(generator, CasualtiesBelowItems.CrudeFilter, "bamboo")
    plainVanillaItem(generator, CasualtiesBelowItems.CrudePoppyPaste, "brown_dye")

    // Liquid bottles: tinted potion overlay + untinted glass body.
    bottle(generator, CasualtiesBelowItems.UnfilteredPoppyLiquid, 0x7a5c33)
    bottle(generator, CasualtiesBelowItems.CrudePoppyLiquid, 0x6e1828)
    bottle(generator, CasualtiesBelowItems.RefinedPoppyExtract, 0x9f244d)

    // Buckets: untinted vanilla bucket base + shared tinted liquid overlay.
    bucket(generator, CasualtiesBelowItems.UnfilteredPoppyLiquidBucket, 0x7a5c33)
    bucket(generator, CasualtiesBelowItems.CrudePoppyLiquidBucket, 0x6e1828)
    bucket(generator, CasualtiesBelowItems.RefinedPoppyExtractBucket, 0x9f244d)
  }

  override def generateBlockStateModels(generator: BlockModelGenerators): Unit = {
    cauldron(generator, CasualtiesBelowBlocks.SoakingPoppyCauldron, "poppy_soak")
    cauldron(generator, CasualtiesBelowBlocks.PoppyInfusionCauldron, "poppy_infusion")
  }

  private def plainVanillaItem(
      generator: ItemModelGenerators,
      item: Item,
      vanillaPath: String
  ): Unit =
    generator.itemModelOutput.accept(
      item,
      ItemModelUtils.plainModel(Identifier.withDefaultNamespace(s"item/$vanillaPath"))
    )

  private def bottle(generator: ItemModelGenerators, item: Item, tint: Int): Unit = {
    val path = BuiltInRegistries.ITEM.getKey(item).getPath
    val model = ModelTemplates.TWO_LAYERED_ITEM.create(
      CasualtiesBelowApi.id(s"item/$path"),
      TextureMapping.layered(
        new Material(Identifier.withDefaultNamespace("item/potion_overlay")),
        new Material(Identifier.withDefaultNamespace("item/potion"))
      ),
      generator.modelOutput
    )
    generator.itemModelOutput.accept(
      item,
      ItemModelUtils.tintedModel(model, ItemModelUtils.constantTint(tint))
    )
  }

  private def bucket(generator: ItemModelGenerators, item: Item, tint: Int): Unit = {
    val path = BuiltInRegistries.ITEM.getKey(item).getPath
    val model = ModelTemplates.TWO_LAYERED_ITEM.create(
      CasualtiesBelowApi.id(s"item/$path"),
      TextureMapping.layered(
        new Material(Identifier.withDefaultNamespace("item/bucket")),
        new Material(CasualtiesBelowApi.id("item/bucket_liquid"))
      ),
      generator.modelOutput
    )
    generator.itemModelOutput.accept(
      item,
      ItemModelUtils.tintedModel(
        model,
        ItemModelUtils.constantTint(0xffffff),
        ItemModelUtils.constantTint(tint)
      )
    )
  }

  /** Mirrors the vanilla layered-cauldron blockstate: one variant per fill level, models created
    * from the vanilla cauldron templates with the mod's content texture.
    */
  private def cauldron(generator: BlockModelGenerators, block: Block, content: String): Unit = {
    val mapping = TextureMapping.cauldron(
      new Material(CasualtiesBelowApi.id(s"block/$content"))
    )
    val level1 = ModelTemplates.CAULDRON_LEVEL1.createWithSuffix(
      block,
      "_level1",
      mapping,
      generator.modelOutput
    )
    val level2 = ModelTemplates.CAULDRON_LEVEL2.createWithSuffix(
      block,
      "_level2",
      mapping,
      generator.modelOutput
    )
    val full = ModelTemplates.CAULDRON_FULL.createWithSuffix(
      block,
      "_full",
      mapping,
      generator.modelOutput
    )
    generator.blockStateOutput.accept(
      MultiVariantGenerator
        .dispatch(block)
        .`with`(
          PropertyDispatch
            .initial(LayeredCauldronBlock.LEVEL.nn)
            .select(JInteger.valueOf(1), BlockModelGenerators.plainVariant(level1))
            .select(JInteger.valueOf(2), BlockModelGenerators.plainVariant(level2))
            .select(JInteger.valueOf(3), BlockModelGenerators.plainVariant(full))
        )
    )
  }
}
