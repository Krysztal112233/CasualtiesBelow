package dev.krysztal.casualtiesbelow.physiology.hygiene

import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.DamageTypeTags
import net.minecraft.tags.EntityTypeTags
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseEntityCallback

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowTags
import dev.krysztal.casualtiesbelow.api.event.TraumaStartedCallback
import dev.krysztal.casualtiesbelow.component.ComponentAccess
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataStores
import dev.krysztal.casualtiesbelow.internal.sync.GameplayDataSnapshot
import dev.krysztal.casualtiesbelow.physiology.discomfort.Discomfort

/** Dirtiness event pulses: discrete one-shot grime from combat, digging, food and husbandry,
  * complementing the continuous accrual/wash in [[Dirtiness]].
  *
  * Every pulse rolls with proportional jitter (`pulseJitter`: pulse × (1 ± jitter)) and syncs
  * immediately, following the zombie-hit immune drain precedent. One event settles exactly once:
  * per-hit listeners read the trauma context, which vanilla emits once per accepted damage event.
  *
  * Incoming hits price contact, not injury: unlike the immune drain, the pulse is not gated on
  * `woundsAllowed` — an armor-absorbed zombie slam still coats the player in grime.
  */
object DirtinessSources {

  def register(): Unit = {
    TraumaStartedCallback.EVENT.register { context =>
      val attacker = Option(context.source.getEntity)
      val base = hitDirt(
        zombieFamily = attacker.exists(_.is(EntityTypeTags.ZOMBIES)),
        explosion = context.source.is(DamageTypeTags.IS_EXPLOSION),
        monster = attacker.exists(_.getType.getCategory == MobCategory.MONSTER),
        zombiePulse = CasualtiesBelowConfig.DirtinessZombieHit.get(),
        explosionPulse = CasualtiesBelowConfig.DirtinessExplosion.get(),
        mobPulse = CasualtiesBelowConfig.DirtinessMobHit.get()
      )
      applyPulse(context.player, base)
    }

    ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register { (_, entity, _, source) =>
      entity match {
        // Melee only: projectiles and explosions keep their distance (or settle separately).
        case player: ServerPlayer
            if !source
              .is(DamageTypeTags.IS_PROJECTILE) && !source.is(DamageTypeTags.IS_EXPLOSION) =>
          applyPulse(player, CasualtiesBelowConfig.DirtinessMeleeKill.get())
        case _ => ()
      }
    }

    PlayerBlockBreakEvents.AFTER.register { (level, player, _, state, _) =>
      (player, level.isClientSide()) match {
        case (serverPlayer: ServerPlayer, false) if !state.isAir =>
          val base = digPulse(
            dirty = state.is(CasualtiesBelowTags.DirtyDiggableBlocks),
            dustless = state.is(CasualtiesBelowTags.DustlessDiggableBlocks),
            dirtyPulse = CasualtiesBelowConfig.DirtinessDigDirtyBlock.get(),
            dustlessPulse = CasualtiesBelowConfig.DirtinessDigDustlessBlock.get(),
            basicPulse = CasualtiesBelowConfig.DirtinessDigBasicBlock.get()
          )
          // A dustless break raises nothing at all: skip the jitter roll entirely.
          if (base > 0.0) applyPulse(serverPlayer, base)
        case _ => ()
      }
    }

    UseEntityCallback.EVENT.register { (player, level, hand, entity, _) =>
      (player, level.isClientSide()) match {
        case (serverPlayer: ServerPlayer, false)
            if entity.isInstanceOf[Animal] &&
              HusbandryTools.contains(serverPlayer.getItemInHand(hand).getItem) =>
          applyPulse(serverPlayer, CasualtiesBelowConfig.DirtinessHusbandry.get())
        case _ => ()
      }
      InteractionResult.PASS
    }
  }

  /** Applies the dirtiness pulse of a just-consumed food: the hygiene-risk fraction of its
    * discomfort mean (raw meat, rotten flesh...). Called by the `Consumable` mixin next to the
    * discomfort and immune settlements; discomfort-free food stays clean.
    */
  def onFoodEaten(player: ServerPlayer, stack: ItemStack): Unit = {
    val store = GameplayDataStores.server(player.level().getServer)
    Discomfort.meanOf(stack, GameplayDataSnapshot.capture(store), store).foreach { mean =>
      applyPulse(player, foodDirt(mean, CasualtiesBelowConfig.DirtinessFoodFraction.get()))
    }
  }

  /** Dirtiness of one broken block, before jitter. Digging tiers: dirty blocks coat the hands most,
    * dustless blocks raise nothing, everything else is the basic default. Dirty wins over dustless
    * when a datapack puts a block in both tags.
    */
  private[hygiene] def digPulse(
      dirty: Boolean,
      dustless: Boolean,
      dirtyPulse: Double,
      dustlessPulse: Double,
      basicPulse: Double
  ): Double = {
    if (dirty) dirtyPulse
    else if (dustless) dustlessPulse
    else basicPulse
  }

  /** Dirtiness of one incoming hit, before jitter. Zombie-family contact grime takes precedence
    * over the blast coating, which takes precedence over generic monster melee.
    */
  private[hygiene] def hitDirt(
      zombieFamily: Boolean,
      explosion: Boolean,
      monster: Boolean,
      zombiePulse: Double,
      explosionPulse: Double,
      mobPulse: Double
  ): Double = {
    if (zombieFamily) zombiePulse
    else if (explosion) explosionPulse
    else if (monster) mobPulse
    else 0.0
  }

  /** Food pulse: the hygiene-risk fraction of the food's discomfort mean. */
  private[hygiene] def foodDirt(mean: Double, fraction: Double): Double =
    mean.max(0.0) * fraction.max(0.0)

  /** Rolls one pulse: base × (1 ± jitter), never negative. */
  private[hygiene] def rollPulse(
      base: Double,
      jitterFraction: Double,
      random: RandomSource
  ): Double = {
    val jitter = jitterFraction.max(0.0).min(1.0)
    base.max(0.0) * (1.0 + (random.nextDouble() * 2.0 - 1.0) * jitter)
  }

  private def applyPulse(player: ServerPlayer, base: Double): Unit = {
    if (player.isCreative || player.isSpectator || !player.isAlive) return
    val rolled = rollPulse(base, CasualtiesBelowConfig.DirtinessPulseJitter.get(), player.getRandom)
    if (rolled <= 0.0) return

    val vitals = ComponentAccess.vitals(player)
    val next = (vitals.dirtiness + rolled).min(CasualtiesBelowConfig.MaxDirtiness.get())
    VitalsMutations.setDirtiness(vitals, next)
    VitalsMutations.syncNow(player)
  }

  private val HusbandryTools = Set(Items.SHEARS, Items.BUCKET, Items.BOWL)
}
