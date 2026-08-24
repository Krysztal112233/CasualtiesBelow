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

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.VitalsComponent
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.sync.GameplayDataSnapshot

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
  * ([[allowsEating]], enforced by the `Consumable` mixin); above the vomiting threshold each tick
  * rolls a chance that rises linearly with discomfort, and vomiting removes a fluctuating amount
  * while applying hunger and saturation penalties.
  *
  * All reads go through a [[GameplayDataSnapshot]]: server logic captures the live config and
  * datapack state, client prediction and displays read the last synced snapshot — so the refusal
  * prediction in `canConsume` matches the server's decision even when the server runs
  * world-datapack overrides or a different config. Untagged food (and beneficial suspicious stew)
  * contributes nothing. Milk keeps working while nauseous: it bears no discomfort, so refusal never
  * blocks it.
  */
object Discomfort {

  /** Resolves the discomfort mean for [stack], or `None` when the food is fine. Lookup order:
    * explicit datapack override, tier tags (most severe tier wins), suspicious-stew effect
    * inspection.
    */
  def meanOf(stack: ItemStack): Option[Double] = meanOf(stack, GameplayDataSnapshot.current)

  /** Snapshot-based overload, so every caller (server logic, client prediction, JEI pages) shares
    * one resolution implementation against one data source.
    */
  def meanOf(stack: ItemStack, data: GameplayDataSnapshot): Option[Double] = {
    data.discomfortMeanOf(stack).map(_._1).orElse(suspiciousStewMean(stack, data))
  }

  /** Whether [player] may start consuming [stack]: past the refusal threshold, food that bears
    * discomfort is rejected. Creative players and discomfort-free food (milk, potions, ordinary
    * meals) are never blocked.
    */
  def allowsEating(player: Player, stack: ItemStack): Boolean = {
    if (player.isCreative || player.isSpectator) return true
    val data = GameplayDataSnapshot.current
    val vitals = CasualtiesBelowComponents.Vitals.get(player)
    if (vitals.discomfort < data.refusalThreshold) return true

    meanOf(stack, data).forall(_ <= 0.0)
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

    if (shouldVomit(vitals.discomfort, player.getRandom)) {
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
    vitals.discomfort = (vitals.discomfort - sampleVomitRelief(player.getRandom)).max(0.0)
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

  private def shouldVomit(discomfort: Double, random: RandomSource): Boolean = {
    val threshold = CasualtiesBelowConfig.DiscomfortVomitChanceThreshold.get()
    if (discomfort <= threshold) return false

    val maxDiscomfort = CasualtiesBelowConfig.MaxDiscomfort.get()
    val progress =
      if (maxDiscomfort <= threshold) 1.0
      else ((discomfort - threshold) / (maxDiscomfort - threshold)).max(0.0).min(1.0)
    val minChance = CasualtiesBelowConfig.DiscomfortVomitMinChancePerTick.get()
    val maxChance =
      math.max(CasualtiesBelowConfig.DiscomfortVomitMaxChancePerTick.get(), minChance)
    random.nextDouble() < minChance + (maxChance - minChance) * progress
  }

  private def sampleVomitRelief(random: RandomSource): Double = {
    val mean = CasualtiesBelowConfig.DiscomfortVomitRelief.get()
    val spread = mean * CasualtiesBelowConfig.DiscomfortVomitReliefSpreadFraction.get()
    mean + (random.nextDouble() * 2.0 - 1.0) * spread
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
  private def suspiciousStewMean(stack: ItemStack, data: GameplayDataSnapshot): Option[Double] = {
    Option(stack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS)).flatMap { effects =>
      val entries = effects.effects().asScala
      if (
        entries.exists { e =>
          e.effect().value() == MobEffects.POISON.value() ||
          e.effect().value() == MobEffects.WITHER.value()
        }
      ) {
        Some(data.discomfortLevelMeans(2))
      } else if (entries.exists(_.effect().value().getCategory == MobEffectCategory.HARMFUL)) {
        Some(data.discomfortLevelMeans(1))
      } else {
        None
      }
    }
  }

  private var ticks = 0
  private val SyncIntervalTicks = 20
  private val NauseaRefreshTicks = 100
  private val VomitNauseaTicks = 300
}
