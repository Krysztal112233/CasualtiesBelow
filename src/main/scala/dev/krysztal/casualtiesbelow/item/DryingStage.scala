package dev.krysztal.casualtiesbelow.item

import java.util.function.Consumer

import com.mojang.serialization.Codec
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponentGetter
import net.minecraft.network.chat.Component
import net.minecraft.util.ExtraCodecs
import net.minecraft.world.item.Item
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.TooltipProvider

/** In-progress shelf drying state carried by an entire item stack.
  *
  * Missing component means fresh (stage 0). Only stages 1 and 2 are stored; the third successful
  * advance converts the whole stack immediately, so no completed stage is persisted.
  */
final case class DryingStage private[item] (value: Int) extends TooltipProvider {

  override def addToTooltip(
      context: Item.TooltipContext,
      consumer: Consumer[Component],
      flag: TooltipFlag,
      components: DataComponentGetter
  ): Unit = {
    consumer.accept(
      Component
        .translatable(
          "tooltip.casualtiesbelow.drying_stage",
          Integer.valueOf(value),
          Integer.valueOf(ShelfDrying.RequiredAdvances)
        )
        .withStyle(ChatFormatting.GRAY)
    )
  }
}

object DryingStage {
  val Codec: Codec[DryingStage] = ExtraCodecs
    .intRange(1, ShelfDrying.RequiredAdvances - 1)
    .xmap(value => DryingStage(value.intValue()), stage => Integer.valueOf(stage.value))

  /** Validates the in-progress range before a stack mutation carries the component. */
  private[casualtiesbelow] def inProgress(value: Int): DryingStage = {
    require(
      value > 0 && value < ShelfDrying.RequiredAdvances,
      s"invalid in-progress drying stage: $value"
    )
    new DryingStage(value)
  }
}
