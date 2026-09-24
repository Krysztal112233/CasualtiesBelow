package dev.krysztal.casualtiesbelow.physiology.consciousness

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Input
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult

import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.PlayerPickItemEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.internal.Consts

/** Query and enforcement entry points for the server-authored unconscious latch.
  *
  * Restrictions deliberately cover voluntary actions only. Vanilla damage, air, gravity, currents,
  * knockback, passengers, portals, and all physiological progression remain untouched. Creative and
  * spectator players bypass restrictions without clearing their stored condition.
  */
object Unconsciousness {

  def restricts(player: Player): Boolean = {
    player.isAlive &&
    !player.isCreative &&
    !player.isSpectator &&
    CasualtiesBelowComponents.Vitals.get(player).consciousness.unconscious
  }

  private val SlownessModifierId = CasualtiesBelow.ofIdentifier("consciousness_slowness")

  /** Incapacitation severity of a scalar in [0, 1]: 0 at and above the start threshold, ramping
    * linearly to 1 at the independent knockout threshold.
    */
  def severityOf(consciousness: Double): Double = {
    val knockout = Consts.Vitals.ConsciousnessKnockoutThreshold
    val start = Consts.Vitals.ConsciousnessImpairmentStartThreshold
    if (start <= knockout) {
      if (consciousness <= knockout) 1.0 else 0.0
    } else {
      ((start - consciousness) / (start - knockout)).max(0.0).min(1.0)
    }
  }

  /** Severity for a player; a latched player is always fully incapacitated. */
  def severity(player: Player): Double = {
    val vitals = CasualtiesBelowComponents.Vitals.get(player)
    if (vitals.consciousness.unconscious) 1.0 else severityOf(vitals.consciousness.level)
  }

  /** Applies the gradual MOVEMENT_SPEED penalty each server tick after consciousness progression.
    * The attribute syncs to the client, keeping movement prediction consistent; full immobility for
    * a latched player is handled separately by PlayerMixin.isImmobile.
    */
  def tickMovementRestriction(player: ServerPlayer): Unit = {
    Option(player.getAttribute(Attributes.MOVEMENT_SPEED)).foreach { attribute =>
      val value =
        if (player.isAlive && !player.isCreative && !player.isSpectator) {
          severity(player)
        } else {
          0.0
        }
      if (value > 0.0) {
        attribute.addOrUpdateTransientModifier(
          new AttributeModifier(
            SlownessModifierId,
            -value,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
          )
        )
      } else {
        attribute.removeModifier(SlownessModifierId)
      }
    }
  }

  def register(): Unit = {
    AttackEntityCallback.EVENT.register((player, _, _, _, _) => interactionResult(player))
    UseBlockCallback.EVENT.register((player, _, _, _) => interactionResult(player))
    UseItemCallback.EVENT.register((player, _, _) => interactionResult(player))
    UseEntityCallback.EVENT.register((player, _, _, _, _) => interactionResult(player))
    PlayerPickItemEvents.BLOCK.register((player, _, _, _) =>
      if (restricts(player)) ItemStack.EMPTY else null
    )
    PlayerPickItemEvents.ENTITY.register((player, _, _) =>
      if (restricts(player)) ItemStack.EMPTY else null
    )
  }

  /** Clears active voluntary actions exactly when unconsciousness begins. The player remains in a
    * vehicle and can still be moved by the world or another controller.
    */
  def onEntered(player: ServerPlayer): Unit = {
    player.stopUsingItem()
    player.setSprinting(false)
    player.stopFallFlying()
    if (player.getAbilities.flying) {
      player.getAbilities.flying = false
      player.onUpdateAbilities()
    }
    player.setShiftKeyDown(false)
    player.setLastClientInput(Input.EMPTY)
    if (player.isSleeping) {
      player.stopSleepInBed(false, true)
    }
  }

  private def interactionResult(player: Player): InteractionResult = {
    if (restricts(player)) InteractionResult.FAIL else InteractionResult.PASS
  }
}
