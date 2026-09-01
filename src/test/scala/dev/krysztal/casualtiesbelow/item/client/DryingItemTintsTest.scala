package dev.krysztal.casualtiesbelow.item.client

import java.util.Collections
import java.util.Optional

import com.mojang.serialization.JsonOps
import net.minecraft.SharedConstants
import net.minecraft.client.color.item.Constant
import net.minecraft.client.color.item.ItemTintSource
import net.minecraft.client.color.item.ItemTintSources
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.item.CuboidItemModelWrapper
import net.minecraft.client.renderer.item.EmptyModel
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.Bootstrap
import net.minecraft.util.ARGB
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

import dev.krysztal.casualtiesbelow.item.CasualtiesBelowDataComponents
import dev.krysztal.casualtiesbelow.item.DryingStage

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

final class DryingItemTintsTest {

  DryingItemTintsTest.initialize

  @Test
  def customTintCodecIsRegisteredAndRoundTripsItsFallback(): Unit = {
    val json = JsonParser.parseString(
      """{"type":"casualtiesbelow:drying_stage","fallback":{"type":"minecraft:constant","value":3368601}}"""
    )
    val decoded = ItemTintSources.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow()
    val stack = new ItemStack(Items.VINE)

    assertTrue(decoded.isInstanceOf[DryingTint])
    assertEquals(ARGB.opaque(0x336699), calculate(decoded, stack))

    val encoded = ItemTintSources.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow()
    val roundTripped = ItemTintSources.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow()
    assertEquals(ARGB.opaque(0x336699), calculate(roundTripped, stack))
  }

  @Test
  def tintDelegatesFreshStacksAndColorsBothDryingStages(): Unit = {
    val fallback = new Constant(0x336699)
    val tint = DryingTint(fallback)
    val stack = new ItemStack(Items.VINE)

    assertEquals(ARGB.opaque(0x336699), calculate(tint, stack))

    stack.set(CasualtiesBelowDataComponents.DryingStageComponent, DryingStage(1))
    assertEquals(DryingTint.Stage1Color, calculate(tint, stack))

    stack.set(CasualtiesBelowDataComponents.DryingStageComponent, DryingStage(2))
    assertEquals(DryingTint.Stage2Color, calculate(tint, stack))
  }

  @Test
  def modelModifierWrapsOnlySupportedFlatItemDefinitions(): Unit = {
    assertEquals(DryingItemTintsTest.SupportedItemIds.toSet, DryingItemTints.SupportedItemIds)

    val fallback = new Constant(0x336699)
    val original = new CuboidItemModelWrapper.Unbaked(
      Identifier.withDefaultNamespace("item/vine"),
      Optional.empty(),
      java.util.List.of(fallback)
    )

    DryingItemTintsTest.SupportedItemIds.foreach { itemId =>
      val result = DryingItemTints.wrapIfSupported(original, itemId)
      assertNotSame(original, result)
      assertTrue(result.isInstanceOf[CuboidItemModelWrapper.Unbaked])
      val wrapped = result.asInstanceOf[CuboidItemModelWrapper.Unbaked]
      assertEquals(original.model(), wrapped.model())
      assertEquals(original.transformation(), wrapped.transformation())
      assertTrue(wrapped.tints().get(0).isInstanceOf[DryingTint])
      assertSame(
        fallback,
        wrapped.tints().get(0).asInstanceOf[DryingTint].fallback
      )
    }

    assertSame(
      original,
      DryingItemTints.wrapIfSupported(original, Identifier.withDefaultNamespace("paper"))
    )

    val complexReplacement = new EmptyModel.Unbaked()
    assertSame(
      complexReplacement,
      DryingItemTints.wrapIfSupported(
        complexReplacement,
        Identifier.withDefaultNamespace("vine")
      )
    )
  }

  @Test
  def untintedFlatModelsReceiveAnIdentityFallback(): Unit = {
    val original = new CuboidItemModelWrapper.Unbaked(
      Identifier.withDefaultNamespace("item/weeping_vines"),
      Optional.empty(),
      Collections.emptyList[ItemTintSource]()
    )
    val wrapped = DryingItemTints
      .wrapIfSupported(original, Identifier.withDefaultNamespace("weeping_vines"))
      .asInstanceOf[CuboidItemModelWrapper.Unbaked]
    val tint = wrapped.tints().get(0).asInstanceOf[DryingTint]

    assertEquals(-1, calculate(tint, new ItemStack(Items.VINE)))
  }

  private def calculate(tint: ItemTintSource, stack: ItemStack): Int = {
    tint.calculate(
      stack,
      null.asInstanceOf[ClientLevel],
      null.asInstanceOf[LivingEntity]
    )
  }
}

private object DryingItemTintsTest {
  val SupportedItemIds: Seq[Identifier] = Seq(
    "vine",
    "weeping_vines",
    "twisting_vines",
    "short_grass",
    "tall_grass",
    "fern",
    "large_fern",
    "short_dry_grass",
    "tall_dry_grass",
    "leaf_litter"
  ).map(Identifier.withDefaultNamespace)

  lazy val initialize: Unit = {
    SharedConstants.tryDetectVersion()
    Bootstrap.bootStrap()
    BuiltInRegistries.ITEM
      .wrapAsHolder(Items.VINE)
      .asInstanceOf[Holder.Reference[Item]]
      .bindComponents(DataComponents.COMMON_ITEM_COMPONENTS)
    ItemTintSources.bootstrap()
    DryingItemTints.register()
  }
}
