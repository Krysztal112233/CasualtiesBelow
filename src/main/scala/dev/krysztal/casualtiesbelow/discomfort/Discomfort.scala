package dev.krysztal.casualtiesbelow.discomfort

import scala.jdk.CollectionConverters.*

import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowTags
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.VitalsComponent
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

/** Food discomfort: how revolting what you just ate was. Which food is how revolting is content —
  * datapack-driven via the three tier tags ([[CasualtiesBelowTags.Discomfort1Items]] and up) with
  * per-item [[DiscomfortOverrides]] on top; what a tier *costs* and how the value floats, decays
  * and escalates is balancing, read from the `[discomfort]` config section.
  *
  * The mean is sampled per bite (gaussian or uniform, config-selected) and then modulated by the
  * player's state instead of leaning on RNG alone: eating while already nauseous, force-feeding on
  * a full stomach, and eating while septic or barely conscious all multiply the dose, so the system
  * stays learnable — "don't keep eating while sick" is a rule players can discover.
  *
  * Consequences are threshold-banded (all config): past the nausea threshold the screen distortion
  * effect is kept up; past the refusal threshold discomfort-bearing food can no longer be started
  * ([[allowsEating]], enforced by the `Consumable` mixin); past the vomit threshold the player
  * throws up: hunger and saturation penalties, and relief down to a fraction of the maximum.
  *
  * Untagged food (and beneficial suspicious stew) contributes nothing. Milk keeps working while
  * nauseous: it bears no discomfort, so refusal never blocks it.
  */
object Discomfort {

  /** Resolves the discomfort mean for [stack], or `None` when the food is fine. Lookup order:
    * explicit datapack override, tier tags (most severe tier wins), suspicious-stew effect
    * inspection.
    */
  def meanOf(stack: ItemStack): Option[Double] = {
    DiscomfortOverrides.forStack(stack) match {
      case Some(entry) =>
        Some(entry.mean.getOrElse(levelMean(entry.level.get)))
      case None =>
        if (stack.is(CasualtiesBelowTags.Discomfort3Items)) {
          Some(CasualtiesBelowConfig.DiscomfortLevel3Mean.get())
        } else if (stack.is(CasualtiesBelowTags.Discomfort2Items)) {
          Some(CasualtiesBelowConfig.DiscomfortLevel2Mean.get())
        } else if (stack.is(CasualtiesBelowTags.Discomfort1Items)) {
          Some(CasualtiesBelowConfig.DiscomfortLevel1Mean.get())
        } else {
          suspiciousStewMean(stack)
        }
    }
  }

  /** Whether [player] may start consuming [stack]: past the refusal threshold, food that bears
    * discomfort is rejected. Creative players and discomfort-free food (milk, potions, ordinary
    * meals) are never blocked.
    */
  def allowsEating(player: Player, stack: ItemStack): Boolean = {
    if (player.isCreative || player.isSpectator) return true
    val vitals = CasualtiesBelowComponents.Vitals.get(player)
    if (vitals.discomfort < CasualtiesBelowConfig.DiscomfortRefusalThreshold.get()) return true

    meanOf(stack).forall(_ <= 0.0)
  }

  /** Applies the discomfort of a just-consumed food item. Called by the `Consumable` mixin on the
    * server when a player finishes eating or drinking something with a food component.
    */
  def onFoodEaten(player: ServerPlayer, stack: ItemStack): Unit = {
    meanOf(stack) match {
      case None                    => ()
      case Some(mean) if mean <= 0 => ()
      case Some(mean)              =>
        val vitals = CasualtiesBelowComponents.Vitals.get(player)
        var amount = sample(mean, player.getRandom)
        if (vitals.discomfort >= CasualtiesBelowConfig.DiscomfortNauseaThreshold.get()) {
          amount *= CasualtiesBelowConfig.DiscomfortNauseousMultiplier.get()
        }
        if (!player.getFoodData.needsFood()) {
          amount *= CasualtiesBelowConfig.DiscomfortOvereatingMultiplier.get()
        }
        if (
          vitals.sepsis > 0.0 ||
          vitals.consciousness < CasualtiesBelowConfig.DiscomfortPoorConditionConsciousness.get()
        ) {
          amount *= CasualtiesBelowConfig.DiscomfortPoorConditionMultiplier.get()
        }
        vitals.discomfort =
          (vitals.discomfort + amount).min(CasualtiesBelowConfig.MaxDiscomfort.get())
        CasualtiesBelowComponents.Vitals.sync(player)
    }
  }

  def register(): Unit = {
    ServerTickEvents.END_SERVER_TICK.register { server =>
      ticks += 1
      val syncTick = ticks % SyncIntervalTicks == 0
      server.getPlayerList.getPlayers.forEach { player =>
        tickPlayer(player, syncTick)
      }
    }
  }

  private def tickPlayer(player: ServerPlayer, syncTick: Boolean): Unit = {
    if (player.isCreative || player.isSpectator || !player.isAlive) return

    val vitals = CasualtiesBelowComponents.Vitals.get(player)

    // Decay: fast while merely queasy ("tough it out"), slow once actually sick, so high
    // discomfort asks for active resolution (or a vomit) instead of being waited out.
    if (vitals.discomfort > 0.0) {
      val rate =
        if (vitals.discomfort < CasualtiesBelowConfig.DiscomfortNauseaThreshold.get()) {
          CasualtiesBelowConfig.DiscomfortDecayLowPerSecond.get()
        } else {
          CasualtiesBelowConfig.DiscomfortDecayHighPerSecond.get()
        }
      vitals.discomfort = (vitals.discomfort - rate / 20.0).max(0.0)
      if (syncTick) CasualtiesBelowComponents.Vitals.sync(player)
    }

    if (vitals.discomfort >= CasualtiesBelowConfig.DiscomfortNauseaThreshold.get()) {
      player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NauseaRefreshTicks, 0))
    }

    if (vitals.discomfort >= CasualtiesBelowConfig.DiscomfortVomitThreshold.get()) {
      vomit(player, vitals)
    }
  }

  private def vomit(player: ServerPlayer, vitals: VitalsComponent): Unit = {
    val food = player.getFoodData
    food.setFoodLevel(
      (food.getFoodLevel - CasualtiesBelowConfig.DiscomfortVomitHungerPenalty.get()).max(0)
    )
    food.setSaturation(
      (food.getSaturationLevel - CasualtiesBelowConfig.DiscomfortVomitSaturationPenalty
        .get()
        .floatValue)
        .max(0.0f)
    )
    vitals.discomfort = CasualtiesBelowConfig.MaxDiscomfort
      .get() * CasualtiesBelowConfig.DiscomfortVomitResetFraction.get()
    player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, VomitNauseaTicks, 1))
    player
      .level()
      .playSound(
        null,
        player.blockPosition(),
        SoundEvents.PLAYER_BURP,
        SoundSource.PLAYERS,
        1.0f,
        0.8f
      )
    CasualtiesBelowComponents.Vitals.sync(player)
  }

  /** The configured mean of a discomfort tier; out-of-range levels collapse to the severe tier
    * (override entries are validated to 1-3 at load time, this is just exhaustiveness).
    */
  private def levelMean(level: Int): Double = level match {
    case 1 => CasualtiesBelowConfig.DiscomfortLevel1Mean.get()
    case 2 => CasualtiesBelowConfig.DiscomfortLevel2Mean.get()
    case _ => CasualtiesBelowConfig.DiscomfortLevel3Mean.get()
  }

  /** Samples one dose around [mean]; the spread scales with the mean so every tier wobbles
    * proportionally ([[CasualtiesBelowConfig.DiscomfortSpreadFraction]]).
    */
  private def sample(mean: Double, random: RandomSource): Double = {
    val spread = mean * CasualtiesBelowConfig.DiscomfortSpreadFraction.get()
    val sampled =
      CasualtiesBelowConfig.DiscomfortDistribution.get() match {
        case "uniform" => mean + (random.nextDouble() * 2.0 - 1.0) * spread
        case _         => mean + random.nextGaussian() * spread
      }
    sampled.max(0.0)
  }

  /** Suspicious stew carries its effects per stack, so it cannot sit in a fixed tier tag: poison
    * and wither are revolting (tier 3), other harmful effects mildly so (tier 2), and beneficial
    * stews are fine.
    */
  private def suspiciousStewMean(stack: ItemStack): Option[Double] = {
    val effects = stack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS)
    if (effects == null) return None

    val entries = effects.effects().asScala
    if (
      entries.exists { e =>
        e.effect().value() == MobEffects.POISON.value() ||
        e.effect().value() == MobEffects.WITHER.value()
      }
    ) {
      Some(CasualtiesBelowConfig.DiscomfortLevel3Mean.get())
    } else if (entries.exists(_.effect().value().getCategory == MobEffectCategory.HARMFUL)) {
      Some(CasualtiesBelowConfig.DiscomfortLevel2Mean.get())
    } else {
      None
    }
  }

  private var ticks = 0
  private val SyncIntervalTicks = 20
  private val NauseaRefreshTicks = 100
  private val VomitNauseaTicks = 300
}
