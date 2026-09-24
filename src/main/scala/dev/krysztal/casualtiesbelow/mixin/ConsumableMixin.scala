package dev.krysztal.casualtiesbelow.mixin

import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.Consumable
import net.minecraft.world.level.Level

import dev.krysztal.casualtiesbelow.internal.extension.Prelude.*
import dev.krysztal.casualtiesbelow.physiology.dirtiness.DirtinessSources
import dev.krysztal.casualtiesbelow.physiology.discomfort.Discomfort
import dev.krysztal.casualtiesbelow.physiology.infection.FoodImmunity

import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** Wires food consequences into the vanilla consumption flow: refusing to start eating while sick
  * (`canConsume`, both sides so the prediction matches) and applying the discomfort dose and immune
  * settlement once consumption completes (`onConsume`, server only).
  */
@Mixin(value = Array(classOf[Consumable]), remap = false)
abstract class ConsumableMixin {

  @Inject(method = Array("canConsume"), at = Array(new At(value = "HEAD")), cancellable = true)
  private def casualtiesbelow$refuseWhenSick(
      user: LivingEntity,
      stack: ItemStack,
      cir: CallbackInfoReturnable[Boolean]
  ): Unit = {
    user match {
      case serverPlayer: ServerPlayer if !Discomfort.allowsEating(serverPlayer, stack) =>
        serverPlayer.sendSystemMessage(
          "message.casualtiesbelow.discomfort.refused".translatable(),
          true
        )
        cir.setReturnValue(false)
      case player: Player if !Discomfort.allowsEating(player, stack) =>
        cir.setReturnValue(false)
      case _ => ()
    }
  }

  @Inject(method = Array("onConsume"), at = Array(new At(value = "HEAD")))
  private def casualtiesbelow$foodConsequencesOnConsume(
      level: Level,
      user: LivingEntity,
      stack: ItemStack,
      cir: CallbackInfoReturnable[ItemStack]
  ): Unit = {
    (user, level.isClientSide()) match {
      case (player: ServerPlayer, false) if stack.has(DataComponents.FOOD) =>
        Discomfort.onFoodEaten(player, stack)
        FoodImmunity.onFoodEaten(player, stack)
        DirtinessSources.onFoodEaten(player, stack)
      case _ => ()
    }
  }
}
