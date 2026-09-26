package dev.krysztal.casualtiesbelow.physiology.dirtiness

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
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataStores
import dev.krysztal.casualtiesbelow.internal.extension.Prelude.*
import dev.krysztal.casualtiesbelow.internal.sync.GameplayDataSnapshot
import dev.krysztal.casualtiesbelow.physiology.discomfort.Discomfort

/** Dirtiness event pulses: discrete one-shot grime from combat, digging, food and husbandry,
  * complementing the continuous accrual/wash in [[Dirtiness]].
  *
  * Every pulse rolls with fixed proportional jitter (pulse × (1 ± jitter)), is scaled by the dirt
  * accumulation setting, and syncs immediately, following the zombie-hit immune drain precedent.
  * One event settles exactly once: per-hit listeners read the trauma context, which vanilla emits
  * once per accepted damage event.
  *
  * Incoming hits price contact, not injury: unlike the immune drain, the pulse is not gated on
  * `woundsAllowed` — an armor-absorbed zombie slam still coats the player in grime.
  */
object DirtinessSources {

  def register(): Unit = {
    TraumaStartedCallback.EVENT.register { context =>
      val attacker = Option(context.source.getEntity)
      val combat = Consts.Dirtiness.CombatPulseDirt
      val base = hitDirt(
        zombieFamily = attacker.exists(_.is(EntityTypeTags.ZOMBIES)),
        explosion = context.source.is(DamageTypeTags.IS_EXPLOSION),
        monster = attacker.exists(_.getType.getCategory == MobCategory.MONSTER),
        zombiePulse = combat,
        explosionPulse = combat * ExplosionCombatWeight,
        mobPulse = combat * MobHitCombatWeight
      )
      applyPulse(context.player, base)
    }

    ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register { (_, entity, _, source) =>
      entity match {
        // Melee only: projectiles and explosions keep their distance (or settle separately).
        case player: ServerPlayer
            if !source
              .is(DamageTypeTags.IS_PROJECTILE) && !source.is(DamageTypeTags.IS_EXPLOSION) =>
          applyPulse(
            player,
            Consts.Dirtiness.CombatPulseDirt * MeleeKillCombatWeight
          )
        case _ => ()
      }
    }

    PlayerBlockBreakEvents.AFTER.register { (level, player, _, state, _) =>
      (player, level.isClientSide()) match {
        case (serverPlayer: ServerPlayer, false) if !state.isAir =>
          val digging = Consts.Dirtiness.DiggingPulseDirt
          val base = digPulse(
            dirty = state.is(CasualtiesBelowTags.DiggableDirtyBlocks),
            dustless = state.is(CasualtiesBelowTags.DiggableDustlessBlocks),
            dirtyPulse = digging * DirtyDiggingWeight,
            dustlessPulse = 0.0,
            basicPulse = digging
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
          applyPulse(serverPlayer, Consts.Dirtiness.InteractionPulseDirt)
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
      applyPulse(player, foodDirt(mean, Consts.Dirtiness.FoodDirtFraction))
    }
  }

  /** Dirtiness of one broken block, before jitter. Digging tiers: dirty blocks coat the hands most,
    * dustless blocks raise nothing, everything else is the basic default. Dirty wins over dustless
    * when a datapack puts a block in both tags.
    */
  private[dirtiness] def digPulse(
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
  private[dirtiness] def hitDirt(
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
  private[dirtiness] def foodDirt(mean: Double, fraction: Double): Double =
    mean.max(0.0) * fraction.max(0.0)

  /** One setting scales every dirt pulse, including combat, digging and contaminated food. */
  private[dirtiness] def scaledPulse(base: Double, multiplier: Double): Double =
    base.max(0.0) * multiplier.max(0.0)

  /** Rolls one pulse: base × (1 ± jitter), never negative. */
  private[dirtiness] def rollPulse(
      base: Double,
      jitterFraction: Double,
      random: RandomSource
  ): Double = {
    val jitter = jitterFraction.max(0.0).min(1.0)
    base.max(0.0) * (1.0 + (random.nextDouble() * 2.0 - 1.0) * jitter)
  }

  private def applyPulse(player: ServerPlayer, base: Double): Unit = {
    if (player.isCreative || player.isSpectator || !player.isAlive) return
    val scaled = scaledPulse(
      base,
      CasualtiesBelowConfig.diseaseHygiene.dirtAccumulationMultiplier.get()
    )
    if (scaled <= 0.0) return
    val rolled = rollPulse(scaled, Consts.Randomness.WorldPulseJitter, player.getRandom)
    if (rolled <= 0.0) return

    val vitals = player.vitals
    val next = (vitals.dirtiness + rolled).min(Consts.Dirtiness.MaxValue)
    vitals.setDirtiness(next)
  }

  private val HusbandryTools = Set(Items.SHEARS, Items.BUCKET, Items.BOWL)

  /** Relative pulse weights within the combat tier (base = one zombie-family hit), preserving the
    * pre-tier defaults: explosions coat 5/3, generic monster hits 1/3, melee kills 0.8/3.
    */
  private val ExplosionCombatWeight = 5.0 / 3.0
  private val MobHitCombatWeight = 1.0 / 3.0
  private val MeleeKillCombatWeight = 0.8 / 3.0

  /** Relative pulse weight of loose (dirty) blocks within the digging tier (base = one ordinary
    * block), preserving the pre-tier 2:1 ratio; dustless blocks raise nothing at all.
    */
  private val DirtyDiggingWeight = 2.0
}
