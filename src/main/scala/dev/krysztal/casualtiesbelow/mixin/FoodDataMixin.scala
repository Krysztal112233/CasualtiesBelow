package dev.krysztal.casualtiesbelow.mixin

import java.lang.Boolean as JBoolean

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.food.FoodData

import dev.krysztal.casualtiesbelow.progression.StarvationProgression

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

/** Stops vanilla starvation hurt calls once custom physiology says no pulse is eligible.
  *
  * The narrow wrapped invoke is the sole `ServerPlayer.hurtServer` call in Minecraft 26.2's
  * `FoodData.tick`. Allowed calls remain vanilla-owned so Fabric `AFTER_DAMAGE` receives the exact
  * accepted source and amount for end-of-tick blood translation.
  */
@Mixin(value = Array(classOf[FoodData]), remap = false)
abstract class FoodDataMixin {

  @WrapOperation(
    method = Array("tick"),
    at = Array(
      new At(
        value = "INVOKE",
        target =
          "Lnet/minecraft/server/level/ServerPlayer;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"
      )
    ),
    remap = false
  )
  private def casualtiesbelow$wrapStarvationPulse(
      player: ServerPlayer,
      level: ServerLevel,
      source: DamageSource,
      amount: Float,
      original: Operation[JBoolean],
      _tickPlayer: ServerPlayer
  ): Boolean = {
    if (!StarvationProgression.shouldApplyVanillaPulse(player)) return false

    original.call(player, level, source, amount).booleanValue
  }
}
