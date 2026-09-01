package dev.krysztal.casualtiesbelow.item

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.stats.Stats
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.level.Level

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.api.body.limb.LimbSnapshot
import dev.krysztal.casualtiesbelow.bleeding.BleedingCalc
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.component.MutableLimbState

/** Reusable basic bandage. Holding use for two seconds treats one automatically selected limb;
  * interrupted uses and uses without an eligible injury have no effect and cost no durability.
  */
final class BasicBandageItem(properties: Item.Properties) extends Item(properties) {

  override def getUseDuration(stack: ItemStack, user: LivingEntity): Int =
    BasicBandageItem.UseDurationTicks

  override def getUseAnimation(stack: ItemStack): ItemUseAnimation = ItemUseAnimation.BRUSH

  override def use(level: Level, player: Player, hand: InteractionHand): InteractionResult = {
    if (!BasicBandageItem.hasTreatableInjury(player)) {
      if (!level.isClientSide()) {
        BasicBandageItem.notifyNoTreatableInjury(player)
      }
      return InteractionResult.FAIL
    }

    player.startUsingItem(hand)
    InteractionResult.CONSUME
  }

  override def finishUsingItem(
      stack: ItemStack,
      level: Level,
      entity: LivingEntity
  ): ItemStack = {
    entity match {
      case player: ServerPlayer =>
        BasicBandageItem.treatBestLimb(player) match {
          case Some(part) =>
            player.awardStat(Stats.ITEM_USED.get(this))
            level.playSound(
              null,
              player.blockPosition(),
              SoundEvents.WOOL_PLACE,
              SoundSource.PLAYERS,
              0.8f,
              1.1f
            )
            player.sendOverlayMessage(
              Component.translatable(
                "message.casualtiesbelow.bandage.applied",
                Component.translatable(s"bodypart.casualtiesbelow.${part.id}")
              )
            )
            stack.hurtAndBreak(1, player, player.getUsedItemHand())
          case None => BasicBandageItem.notifyNoTreatableInjury(player)
        }
      case _ => ()
    }
    stack
  }
}

object BasicBandageItem {
  val MaxUses = 8
  val UseDurationTicks = 40

  private[item] val SkinRestored = 10.0
  private[item] val MuscleRestored = 5.0

  /** 1 mL/s in the component's mL/tick storage unit. */
  private[item] val BleedingReducedPerTick = 0.05

  private def notifyNoTreatableInjury(player: Player): Unit = {
    player.sendOverlayMessage(
      Component.translatable("message.casualtiesbelow.bandage.no_treatable_injury")
    )
  }

  private def hasTreatableInjury(player: Player): Boolean = {
    val body = CasualtiesBelowComponents.body(player)
    selectTarget(body.stats).nonEmpty
  }

  /** Applies one treatment to the best currently eligible limb. Selection is repeated at finish
    * time so a limb that changed during the use animation cannot consume durability for no effect.
    */
  private def treatBestLimb(player: Player): Option[BodyPart] = {
    val body = CasualtiesBelowComponents.body(player)
    selectTarget(body.stats).filter { part =>
      BodyMutations
        .mutate(player, part, markDirty = true)(applyTreatment)
        .changed
    }
  }

  private[item] def selectTarget(
      statsFor: BodyPart => LimbSnapshot
  ): Option[BodyPart] = {
    BodyPart.values.iterator
      .map(part => part -> statsFor(part))
      .filter((_, stats) => isTreatable(stats))
      .maxByOption { (part, stats) =>
        (
          if (stats.externalBleedingRate > 0.0) 1 else 0,
          tissueDamage(stats),
          stats.externalBleedingRate,
          -part.ordinal
        )
      }
      .map((part, _) => part)
  }

  private[item] def applyTreatment(stats: MutableLimbState): Unit = {
    stats.skinIntegrity = (stats.skinIntegrity + SkinRestored).min(MutableLimbState.MaxValue)
    stats.muscleHealth = (stats.muscleHealth + MuscleRestored).min(MutableLimbState.MaxValue)
    BleedingCalc.applyFixedHemostasis(stats, BleedingReducedPerTick)
  }

  private def isTreatable(stats: LimbSnapshot): Boolean = {
    stats.externalBleedingRate > 0.0 ||
    stats.skinIntegrity < LimbSnapshot.MaxValue ||
    stats.muscleHealth < LimbSnapshot.MaxValue
  }

  private def tissueDamage(stats: LimbSnapshot): Double = {
    (LimbSnapshot.MaxValue - stats.skinIntegrity).max(0.0) +
      (LimbSnapshot.MaxValue - stats.muscleHealth).max(0.0)
  }
}
