package dev.krysztal.casualtiesbelow.item.client

import scala.jdk.CollectionConverters.*

import com.mojang.serialization.MapCodec
import net.minecraft.client.color.item.Constant
import net.minecraft.client.color.item.ItemTintSource
import net.minecraft.client.color.item.ItemTintSources
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.item.CuboidItemModelWrapper
import net.minecraft.client.renderer.item.ItemModel
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi
import dev.krysztal.casualtiesbelow.internal.extension.ItemStackExtensions.*
import dev.krysztal.casualtiesbelow.item.FiberClothDryingInputs

/** Adds stack-component-driven drying tints to the vanilla flat models of the built-in fiber
  * inputs. Complex resource-pack replacements are left untouched rather than being wrapped
  * incorrectly.
  */
@Environment(EnvType.CLIENT)
object DryingItemTints {
  private[item] lazy val SupportedItemIds: Set[Identifier] =
    FiberClothDryingInputs.BuiltInItems
      .map(item => BuiltInRegistries.ITEM.getResourceKey(item).orElseThrow().identifier())
      .toSet

  def register(): Unit = {
    ItemTintSources.ID_MAPPER.put(
      CasualtiesBelowApi.id("drying_stage"),
      DryingTint.Codec
    )
    ModelLoadingPlugin.register { pluginContext =>
      val modifier: ModelModifier.BeforeBakeItem = (model, context) => {
        wrapIfSupported(model, context.itemId())
      }
      pluginContext
        .modifyItemModelBeforeBake()
        .register(ModelModifier.WRAP_PHASE, modifier)
    }
  }

  private[item] def wrapIfSupported(
      model: ItemModel.Unbaked,
      itemId: Identifier
  ): ItemModel.Unbaked = {
    if (SupportedItemIds.contains(itemId)) wrap(model) else model
  }

  private def wrap(model: ItemModel.Unbaked): ItemModel.Unbaked = {
    model match {
      case cuboid: CuboidItemModelWrapper.Unbaked =>
        val originalTints: Seq[ItemTintSource] = {
          if (cuboid.tints().isEmpty) Seq(new Constant(0xffffff))
          else cuboid.tints().asScala.toSeq
        }
        val wrappedTints: Seq[ItemTintSource] = originalTints.map {
          case tint: DryingTint => tint
          case tint             => DryingTint(tint)
        }
        new CuboidItemModelWrapper.Unbaked(
          cuboid.model(),
          cuboid.transformation(),
          java.util.List.copyOf(wrappedTints.asJava)
        )
      case _ => model
    }
  }
}

@Environment(EnvType.CLIENT)
private[item] final case class DryingTint(fallback: ItemTintSource) extends ItemTintSource {
  override def calculate(
      itemStack: ItemStack,
      level: ClientLevel,
      owner: LivingEntity
  ): Int = {
    itemStack.dryingStage match {
      case 1 => DryingTint.Stage1Color
      case 2 => DryingTint.Stage2Color
      case _ => fallback.calculate(itemStack, level, owner)
    }
  }

  override def `type`(): MapCodec[DryingTint] = DryingTint.Codec
}

@Environment(EnvType.CLIENT)
private[item] object DryingTint {
  val Stage1Color: Int = ARGB.opaque(0xead99a)
  val Stage2Color: Int = ARGB.opaque(0xc8a458)

  val Codec: MapCodec[DryingTint] = ItemTintSources.CODEC
    .fieldOf("fallback")
    .xmap(fallback => DryingTint(fallback), tint => tint.fallback)
}
