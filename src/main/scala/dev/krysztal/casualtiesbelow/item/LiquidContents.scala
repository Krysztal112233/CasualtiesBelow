package dev.krysztal.casualtiesbelow.item

import java.lang.Long as JLong
import java.util.function.Consumer

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponentGetter
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.ExtraCodecs
import net.minecraft.world.item.Item
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.TooltipProvider

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi

/** Logical liquid identity and amount stored in a container item without requiring a registered
  * world fluid. Amounts use Fabric transfer droplets so a future Storage adapter can reuse them.
  */
final case class LiquidContents private[item] (liquid: Identifier, droplets: Long)
    extends TooltipProvider {

  override def addToTooltip(
      context: Item.TooltipContext,
      consumer: Consumer[Component],
      flag: TooltipFlag,
      components: DataComponentGetter
  ): Unit = {
    consumer.accept(
      Component
        .translatable(
          "tooltip.casualtiesbelow.liquid_amount",
          JLong.valueOf(droplets)
        )
        .withStyle(ChatFormatting.GRAY)
    )
  }
}

object LiquidContents {
  val AmpouleDroplets: Long = 810L

  val CrudePoppyLiquid: LiquidContents =
    LiquidContents(CasualtiesBelowApi.id("crude_poppy_liquid"), FluidConstants.BOTTLE)
  val RefinedPoppyExtract: LiquidContents = LiquidContents(
    CasualtiesBelowApi.id("refined_poppy_extract"),
    PoppyRefining.refinedDroplets(FluidConstants.BOTTLE)
  )
  val RefinedPoppyAmpoule: LiquidContents =
    LiquidContents(RefinedPoppyExtract.liquid, AmpouleDroplets)

  val Codec: Codec[LiquidContents] = RecordCodecBuilder.create(instance =>
    instance
      .group(
        Identifier.CODEC.fieldOf("liquid").forGetter(_.liquid),
        ExtraCodecs.POSITIVE_LONG
          .fieldOf("amount")
          .forGetter(contents => JLong.valueOf(contents.droplets))
      )
      .apply(instance, (liquid, amount) => LiquidContents(liquid, amount.longValue()))
  )
}
