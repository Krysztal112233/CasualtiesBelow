package dev.krysztal.casualtiesbelow.physiology.discomfort

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.RandomSource
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowTags
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataLookup
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataStore
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataStores
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*
import dev.krysztal.casualtiesbelow.internal.sync.GameplayDataSnapshot
import dev.krysztal.casualtiesbelow.physiology.dirtiness.Dirtiness
import dev.krysztal.casualtiesbelow.physiology.opioid.OpioidWithdrawal
import dev.krysztal.casualtiesbelow.progression.AchievementHooks

/** Probability distribution used when sampling a food's discomfort dose around its mean. */
enum DiscomfortDistribution extends Enum[DiscomfortDistribution] {
  case Gaussian
  case Uniform
}

/** Food discomfort: how revolting what you just ate was. Which food is how revolting is content —
  * datapack-driven via the three tier tags ([[CasualtiesBelowTags.Items.Discomfort1Food]] and up)
  * with per-item `discomfort` datapack entries on top. Tier means, sampling and decay are fixed
  * balance values in [[Consts.Discomfort]]; players can adjust the food-refusal threshold.
  *
  * The mean is sampled with a Gaussian distribution per bite and then modulated by the player's
  * state instead of leaning on RNG alone: eating while already nauseous, force-feeding on a full
  * stomach, and eating while septic or barely conscious all multiply the dose, so the system stays
  * learnable — "don't keep eating while sick" is a rule players can discover.
  *
  * Consequences: nausea arrives as a per-tick probability that scales with discomfort, each success
  * holding the screen distortion briefly so queasiness comes in waves rather than a guaranteed
  * state; past the refusal threshold discomfort-bearing food can no longer be started
  * ([[allowsEating]], enforced by the `Consumable` mixin); above the vomiting threshold each tick
  * rolls a chance that rises linearly with discomfort, and vomiting removes a fluctuating amount
  * while applying hunger and saturation penalties.
  *
  * Authoritative per-item entries come from the reload-listener store on the server and the synced
  * store on clients: `food/item/<ns>/<path>.json` files can re-assign the tier or price the mean
  * directly (see `data.schema.FoodEffectsData`). Global means and thresholds still come from
  * [[GameplayDataSnapshot]]. Untagged food (and beneficial suspicious stew) contributes nothing.
  * Milk keeps working while nauseous: it bears no discomfort, so refusal never blocks it.
  *
  * Pure math (decay pacing, the chance ramp, dose sampling) lives in [[DiscomfortCalc]].
  */
object Discomfort {

  /** Resolves the discomfort mean for [stack], or `None` when the food is fine. Lookup order:
    * explicit datapack entry, tier tags (most severe tier wins), suspicious-stew effect inspection.
    */
  def meanOf(stack: ItemStack)(using
      data: GameplayDataSnapshot,
      store: GameplayDataStore
  ): Option[Double] = {
    meanOfWithTier(stack)
      .map(_._1)
      .orElse(suspiciousStewMean(stack))
  }

  /** Resolves the datapack/tag-derived mean and its tier for client displays. Explicit datapack
    * entries take precedence over tier tags; among tags, the most severe tier wins.
    */
  def meanOfWithTier(stack: ItemStack)(using
      data: GameplayDataSnapshot,
      store: GameplayDataStore
  ): Option[(Double, Option[Int])] = {
    explicitMean(stack).orElse(taggedMean(stack))
  }

  /** Whether [player] may start consuming [stack]: past the refusal threshold, food that bears
    * discomfort is rejected. Creative players and discomfort-free food (milk, potions, ordinary
    * meals) are never blocked.
    */
  def allowsEating(player: Player, stack: ItemStack): Boolean = {
    if (player.isCreative || player.isSpectator) return true
    val (data, store) = player match {
      case serverPlayer: ServerPlayer => serverData(serverPlayer)
      case _                          =>
        val snapshot = GameplayDataSnapshot.current
        (snapshot, snapshot.gameplayData)
    }
    given GameplayDataSnapshot = data
    given GameplayDataStore = store
    val vitals = CasualtiesBelowComponents.Vitals.get(player)
    if (vitals.discomfort < data.refusalThreshold) return true

    meanOf(stack).forall(_ <= 0.0)
  }

  /** Applies the discomfort of a just-consumed food item. Called by the `Consumable` mixin on the
    * server when a player finishes eating or drinking something with a food component.
    */
  def onFoodEaten(player: ServerPlayer, stack: ItemStack): Unit = {
    val (data, store) = serverData(player)
    given GameplayDataSnapshot = data
    given GameplayDataStore = store
    meanOf(stack) match {
      case None                    => ()
      case Some(mean) if mean <= 0 => ()
      case Some(mean)              =>
        val vitals = player.vitals
        var amount = sample(mean, player.getRandom)
        if (vitals.discomfort >= Consts.Discomfort.NauseaThreshold) {
          amount *= Consts.Discomfort.AlreadyNauseousMultiplier
        }
        if (!player.getFoodData.needsFood()) {
          amount *= Consts.Discomfort.OvereatingMultiplier
        }
        if (
          vitals.infection.sepsis > 0.0 ||
          vitals.consciousness.level < Consts.Discomfort.PoorConditionConsciousnessThreshold
        ) {
          amount *= Consts.Discomfort.PoorConditionMultiplier
        }
        amount *= Dirtiness.foodDiscomfortMultiplier(
          vitals.dirtiness,
          Consts.Dirtiness.MaxValue,
          Consts.Dirtiness.FoodDiscomfortMultiplierAtMax
        )
        vitals.setDiscomfort(
          (vitals.discomfort + amount).min(Consts.Discomfort.MaxValue)
        )
        AchievementHooks.onFoodDiscomfortSettled(player)
    }
  }

  def register(): Unit = {
    ServerTickEvents.END_SERVER_TICK.register { server =>
      server.getPlayerList.getPlayers.forEach { player =>
        tickPlayer(player)
      }
    }
  }

  /** Server-side snapshot/store pair for discomfort lookups. */
  private def serverData(player: ServerPlayer): (GameplayDataSnapshot, GameplayDataStore) = {
    val store = GameplayDataStores.server(player.level().getServer)
    (GameplayDataSnapshot.capture(store), store)
  }

  private def tickPlayer(player: ServerPlayer): Unit = {
    if (player.isCreative || player.isSpectator || !player.isAlive) return

    val vitals = player.vitals

    if (vitals.discomfort > 0.0) {
      vitals.setDiscomfort(
        DiscomfortCalc.nextAfterOrdinaryDecay(
          vitals.discomfort,
          OpioidWithdrawal.isActive(vitals),
          Consts.Discomfort.NauseaThreshold,
          Consts.Discomfort.DecayRateLowPerSecond,
          Consts.Discomfort.DecayRateHighPerSecond
        )
      )
    }

    if (shouldNauseate(vitals.discomfort, player.getRandom)) {
      player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NauseaDurationTicks, 0))
    }

    if (shouldVomit(vitals.discomfort, player.getRandom)) {
      vomit(player, vitals)
    }
  }

  private def vomit(player: ServerPlayer, vitals: VitalsComponentImpl): Unit = {
    val food = player.getFoodData
    food.setFoodLevel(
      (food.getFoodLevel - Consts.Discomfort.VomitHungerPenalty).max(0)
    )
    food.setSaturation(
      (food.getSaturationLevel - Consts.Discomfort.VomitSaturationPenalty.toFloat)
        .max(0.0f)
    )
    vitals.setDiscomfort(
      (
        vitals.discomfort - DiscomfortCalc.uniformSample(
          Consts.Discomfort.VomitRelief,
          Consts.Randomness.DoseSpreadFraction,
          player.getRandom.nextDouble()
        )
      ).max(0.0)
    )
    player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, VomitNauseaTicks, 1))
    player
      .level()
      .playPlayerSound(player, SoundEvents.PLAYER_BURP, pitch = 0.8f)
  }

  private def shouldVomit(discomfort: Double, random: RandomSource): Boolean = {
    discomfort > Consts.Discomfort.VomitChanceThreshold &&
    random.nextDouble() < DiscomfortCalc.chancePerTick(
      discomfort,
      Consts.Discomfort.VomitChanceThreshold,
      Consts.Discomfort.VomitMinChancePerTick,
      Consts.Discomfort.VomitMaxChancePerTick,
      Consts.Discomfort.MaxValue
    )
  }

  /** Nausea comes in unpredictable waves: a per-tick roll scaled by discomfort, each success
    * holding the screen distortion briefly. Never guaranteed at any discomfort level.
    */
  private def shouldNauseate(discomfort: Double, random: RandomSource): Boolean = {
    discomfort > Consts.Discomfort.NauseaChanceThreshold &&
    random.nextDouble() < DiscomfortCalc.chancePerTick(
      discomfort,
      Consts.Discomfort.NauseaChanceThreshold,
      Consts.Discomfort.NauseaMinChancePerTick,
      Consts.Discomfort.NauseaMaxChancePerTick,
      Consts.Discomfort.MaxValue
    )
  }

  /** Samples one dose around [mean]; the spread scales with the mean so every tier wobbles
    * proportionally ([[Consts.Randomness.DoseSpreadFraction]]), floored at zero.
    */
  private def sample(mean: Double, random: RandomSource): Double = {
    val fraction = Consts.Randomness.DoseSpreadFraction
    val sampled =
      Consts.Discomfort.Distribution match {
        case DiscomfortDistribution.Uniform =>
          DiscomfortCalc.uniformSample(mean, fraction, random.nextDouble())
        case DiscomfortDistribution.Gaussian =>
          DiscomfortCalc.gaussianSample(mean, fraction, random.nextGaussian())
      }
    sampled.max(0.0)
  }

  private def explicitMean(stack: ItemStack)(using
      data: GameplayDataSnapshot,
      store: GameplayDataStore
  ): Option[(Double, Option[Int])] = {
    GameplayDataLookup.foodEffects(stack.typeHolder(), store).flatMap { entry =>
      entry.discomfortTier.toScala
        .map(tier => tierResult(tier.intValue()))
        .orElse(entry.discomfortMean.toScala.map(mean => (mean.doubleValue(), None)))
    }
  }

  private def taggedMean(stack: ItemStack)(using
      data: GameplayDataSnapshot
  ): Option[(Double, Option[Int])] = {
    if (stack.is(CasualtiesBelowTags.Items.Discomfort3Food)) Some(tierResult(3))
    else if (stack.is(CasualtiesBelowTags.Items.Discomfort2Food)) Some(tierResult(2))
    else if (stack.is(CasualtiesBelowTags.Items.Discomfort1Food)) Some(tierResult(1))
    else None
  }

  /** Tier mean paired with the tier itself, for client displays. */
  private def tierResult(tier: Int)(using data: GameplayDataSnapshot): (Double, Option[Int]) =
    (tierMean(tier, data.discomfortLevelMeans), Some(tier))

  private def tierMean(level: Int, means: List[Double]): Double = {
    means.applyOrElse(level - 1, (_: Int) => means.last)
  }

  /** Suspicious stew carries its effects per stack, so it cannot sit in a fixed tier tag: poison
    * and wither are revolting (tier 3), other harmful effects mildly so (tier 2), and beneficial
    * stews are fine.
    */
  private def suspiciousStewMean(stack: ItemStack)(using
      data: GameplayDataSnapshot
  ): Option[Double] = {
    Option(stack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS)).flatMap { effects =>
      val entries = effects.effects().asScala
      if (
        entries.exists { e =>
          val eff = e.effect().value()
          eff == MobEffects.POISON.value() || eff == MobEffects.WITHER.value()
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

  private val NauseaDurationTicks = 100
  private val VomitNauseaTicks = 300
}
