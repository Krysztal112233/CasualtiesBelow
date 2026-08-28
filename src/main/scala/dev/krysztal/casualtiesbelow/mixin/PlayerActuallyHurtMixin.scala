package dev.krysztal.casualtiesbelow.mixin

import java.lang.Void

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.player.Player

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowTags

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

/** Makes blood volume authoritative without replacing the player damage pipeline.
  *
  * The wrapped call is the sole ordinary player-health subtraction in Minecraft 26.2. Everything
  * before and after it still executes; only the value passed to `setHealth` changes. Fatal sources
  * in the datapack-extensible passthrough tag retain vanilla health/death semantics.
  */
@Mixin(value = Array(classOf[Player]), remap = false)
abstract class PlayerActuallyHurtMixin {

  @WrapOperation(
    method = Array("actuallyHurt"),
    at = Array(
      new At(
        value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;setHealth(F)V"
      )
    ),
    remap = false
  )
  private def casualtiesbelow$wrapPlayerHealthDamage(
      player: Player,
      vanillaHealth: Float,
      original: Operation[Void],
      _level: ServerLevel,
      source: DamageSource,
      _damage: Float
  ): Unit = {
    val targetHealth =
      if (source.is(CasualtiesBelowTags.BypassesHealthRedirect)) vanillaHealth
      else player.getHealth
    original.call(player, targetHealth)
  }
}
