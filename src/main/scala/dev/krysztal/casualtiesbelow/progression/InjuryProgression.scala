package dev.krysztal.casualtiesbelow.progression

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.RandomSource

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowDamageTypes
import dev.krysztal.casualtiesbelow.api.LimbInjuries
import dev.krysztal.casualtiesbelow.api.body.BodyComponent
import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.LimbStats
import dev.krysztal.casualtiesbelow.api.body.VitalsComponent
import dev.krysztal.casualtiesbelow.bleeding.BleedingCalc
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

/** Time evolution of injuries: what heals, what worsens, and what kills when left alone.
  *
  * Per server tick and per player (skipped in creative/spectator):
  *
  *   - fractures count down their recovery time and heal when it runs out; walking on a fractured
  *     or dislocated leg strains it, granting pain scaled by the leg's tissue damage (muscle and
  *     skin) at the configured rate
  *   - bleeding drains the blood volume and clots linearly; the per-limb rate is capped
  *     proportionally to the skin damage (see [[BleedingCalc.cap]]); reaching zero blood is fatal
  *     ([[CasualtiesBelowDamageTypes.BloodLoss]])
  *   - wounds with meaningful skin damage can get infected; the immune system fights infections
  *     with its total capacity split across all infected limbs, against per-limb spread rates
  *     proportional to its complement; past a progress ramp an infection can also seed adjacent
  *     limbs (see [[tickInfection]], [[tickContagion]]), and it scales skin regrowth (see
  *     [[tickSkinRegen]])
  *   - immune health is a lifestyle stat decoupled from infection: a full stomach restores it,
  *     hunger drains it (see [[tickImmune]])
  *   - skin regrows only once the wound has clotted shut; muscle regrows regardless (slower)
  *   - pain decays linearly at the configured rate
  *
  * Dislocations never self-heal — they need treatment (not yet implemented).
  *
  * Healing is not an injury: it bypasses `LimbInjuries.apply` (no event, no jitter) and mutates the
  * components directly. Sync is throttled: continuous changes (decay/regen/blood drain) are flushed
  * once per [[SyncIntervalTicks]], discrete transitions (fracture healed, bleeding stopped) flush
  * immediately. Registration order matters: this pipeline must run before `LimbInjuries.register`'s
  * flush so its dirty marks ship in the same tick.
  *
  * The remaining numeric constants are balancing placeholders; expect them to become config values
  * once playtesting starts.
  */
object InjuryProgression {

  def register(): Unit = {
    ServerTickEvents.END_SERVER_TICK.register(tickServer)
    // Components persist through respawn (RespawnCopyStrategy.ALWAYS_COPY) so dimension changes
    // keep the player's condition; a death respawn (alive = false) is a fresh start and resets
    // everything — dying of sepsis must not respawn you still infected.
    ServerPlayerEvents.AFTER_RESPAWN.register { (_, newPlayer, alive) =>
      if (!alive) {
        CasualtiesBelowComponents.reset(newPlayer)
      }
    }
  }

  private def tickServer(server: MinecraftServer): Unit = {
    ticks += 1
    val syncTick = ticks % SyncIntervalTicks == 0
    server.getPlayerList.getPlayers.forEach { player =>
      tickPlayer(player, syncTick)
    }
  }

  private def tickPlayer(player: ServerPlayer, syncTick: Boolean): Unit = {
    if (player.isCreative || player.isSpectator || !player.isAlive) return

    val body = CasualtiesBelowComponents.Body.get(player)
    val vitals = CasualtiesBelowComponents.Vitals.get(player)
    val walking = isWalking(player)
    var totalBleeding = 0.0

    // The immune system splits its fight capacity across all infected limbs: one infection is
    // containable, several at once overwhelm a marginal immune system.
    val infectedCount = BodyPart.values.count(p => body.stats(p).infectionProgress.isDefined)
    val fightShare = 1.0 / infectedCount.max(1)
    var infectionLoad = 0.0

    BodyPart.values.foreach { part =>
      val current = body.stats(part)
      val updated = current.copy()
      val discrete = tickLimb(
        updated,
        walkingStrainRate(part, current, walking),
        vitals.immuneHealth,
        fightShare,
        player.getRandom
      )
      totalBleeding += updated.externalBleedingRate
      infectionLoad += updated.infectionProgress.getOrElse(0.0)
      if (updated != current) {
        body.setStats(part, updated)
        if (discrete || syncTick) {
          LimbInjuries.markDirty(player)
        }
      }
    }

    tickContagion(player, body)

    var vitalsChanged = tickSepsis(vitals, infectionLoad)
    vitalsChanged = tickImmune(vitals, player) || vitalsChanged

    // Sepsis compresses the effective blood cap; well-fed players regenerate blood up to it.
    // Blood over the cap is lost outright: surviving sepsis leaves the body drained, and
    // recovery means eating well.
    val maxBlood = CasualtiesBelowConfig.effectiveMaxBloodVolume(vitals.sepsis)
    if (player.getFoodData.getFoodLevel >= CasualtiesBelowConfig.FedFoodLevelThreshold.get()) {
      val regenerated =
        (vitals.bloodVolume + CasualtiesBelowConfig.FedBloodRegenPerTick.get()).min(maxBlood)
      if (regenerated != vitals.bloodVolume) {
        vitals.bloodVolume = regenerated
        vitalsChanged = true
      }
    }
    if (vitals.bloodVolume > maxBlood) {
      vitals.bloodVolume = maxBlood
      vitalsChanged = true
    }

    if (totalBleeding > 0.0) {
      vitals.bloodVolume = (vitals.bloodVolume - totalBleeding).max(0.0)
      vitalsChanged = true
    }
    if (vitals.bloodVolume <= 0.0) {
      val fatal =
        if (maxBlood <= 0.0) CasualtiesBelowDamageTypes.sepsis(player.level())
        else CasualtiesBelowDamageTypes.bloodLoss(player.level())
      player.hurtServer(player.level(), fatal, Float.MaxValue)
    }

    // Sync on every changing tick, not just SyncIntervalTicks boundaries: clotting or recovery
    // can stop the drain between two periodic syncs, and a skipped final value would only reach
    // the client when the vitals change again.
    if (vitalsChanged) {
      CasualtiesBelowComponents.Vitals.sync(player)
    }
  }

  /** Sepsis is the whole-body consequence of infection: it builds in proportion to the total
    * infection load (the sum of all limbs' infection progress) and recovers at a fixed rate, so
    * below the break-even load it drains away on its own. Its effect is applied where blood is
    * handled: the effective blood volume cap is compressed linearly with sepsis (see
    * [[CasualtiesBelowConfig.effectiveMaxBloodVolume]]), down to zero — fatal — at full sepsis.
    * Returns whether the value changed.
    */
  private def tickSepsis(vitals: VitalsComponent, infectionLoad: Double): Boolean = {
    val maxLoad = LimbStats.MaxValue * BodyPart.values.length
    val gain = CasualtiesBelowConfig.SepsisGainPerTick.get() * infectionLoad / maxLoad
    val next = (vitals.sepsis + gain - CasualtiesBelowConfig.SepsisDecayPerTick.get())
      .max(0.0)
      .min(CasualtiesBelowConfig.MaxSepsis.get())
    if (next == vitals.sepsis) return false

    vitals.sepsis = next
    true
  }

  /** Immune health is a lifestyle stat driven by diet, deliberately decoupled from infection load:
    * being well-fed restores it slowly and hunger drains it (thresholds mirror vanilla's
    * regeneration/sprinting cutoffs). Returns whether the value changed.
    */
  private def tickImmune(vitals: VitalsComponent, player: ServerPlayer): Boolean = {
    val food = player.getFoodData.getFoodLevel
    val delta: Double =
      if (food >= CasualtiesBelowConfig.FedFoodLevelThreshold.get().intValue) {
        CasualtiesBelowConfig.FedImmuneRegenPerTick.get()
      } else if (food < CasualtiesBelowConfig.HungryFoodLevelThreshold.get().intValue) {
        -CasualtiesBelowConfig.HungryImmuneDrainPerTick.get()
      } else {
        0.0
      }
    if (delta == 0.0) return false

    val next =
      (vitals.immuneHealth + delta).max(0.0).min(CasualtiesBelowConfig.MaxImmuneHealth.get())
    if (next == vitals.immuneHealth) return false

    vitals.immuneHealth = next
    true
  }

  /** One tick of evolution for one limb, mutating the given copy in place. `strainPainRate` is the
    * walking-strain pain rate when the limb is a fractured/dislocated leg currently bearing the
    * walking player, zero otherwise. `immuneHealth` modulates infection spread and skin regrowth.
    * Returns whether a discrete transition occurred (fracture healed, bleeding stopped, infection
    * started or cleared).
    *
    * Ordering matters: bleeding clots before skin regrowth is considered, so a wound that seals
    * this tick starts regrowing skin immediately.
    */
  private def tickLimb(
      stats: LimbStats,
      strainPainRate: Double,
      immuneHealth: Double,
      fightShare: Double,
      random: RandomSource
  ): Boolean = {
    val fractureHealed = tickFracture(stats)
    val bleedingStopped = tickBleeding(stats)
    val infectionTransition = tickInfection(stats, immuneHealth, fightShare, random)
    tickInfectionEffects(stats)
    tickSkinRegen(stats, immuneHealth)
    tickMuscleRegen(stats)
    tickPainDecay(stats)
    tickWalkingStrain(stats, strainPainRate)
    fractureHealed || bleedingStopped || infectionTransition
  }

  /** Counts down the fracture recovery time; returns true when the fracture healed this tick. */
  private def tickFracture(stats: LimbStats): Boolean = {
    if (stats.fractureRecoveryTicks.isEmpty) return false

    val remaining = stats.fractureRecoveryTicks.get
    if (remaining <= 1) {
      stats.fractureRecoveryTicks = None
      true
    } else {
      stats.fractureRecoveryTicks = Some(remaining - 1)
      false
    }
  }

  /** Clots an actively bleeding wound linearly (rate capped by the skin damage); returns true when
    * the bleeding stopped this tick.
    */
  private def tickBleeding(stats: LimbStats): Boolean = {
    if (stats.externalBleedingRate <= 0.0) return false

    val capped = stats.externalBleedingRate.min(BleedingCalc.cap(stats.skinIntegrity))
    val clotted = (capped - CasualtiesBelowConfig.ClottingRatePerTick.get()).max(0.0)
    stats.externalBleedingRate = clotted
    clotted == 0.0
  }

  /** Infection onset and progression. Onset is a per-tick Bernoulli roll gated on a meaningful
    * wound (at least [[InfectionSkinDamageThreshold]] skin damage), with probability scaled by the
    * skin damage fraction; an onset seeds a small progress value so a strong immune system gets a
    * visible suppression window instead of an instant flicker. While infected, the immune system
    * fights the infection with its share of the total immune capacity (`fightShare` splits it
    * across all infected limbs) against a spread rate proportional to the immune complement — with
    * the defaults and a single infection, the break-even point is an immune health of 120 out of
    * 200 (see [[CasualtiesBelowConfig.immuneBreakEven]]). Returns true on the discrete transitions
    * (onset, cleared).
    */
  private def tickInfection(
      stats: LimbStats,
      immuneHealth: Double,
      fightShare: Double,
      random: RandomSource
  ): Boolean = {
    val immuneFraction = immuneHealth / CasualtiesBelowConfig.MaxImmuneHealth.get()
    stats.infectionProgress match {
      case Some(progress) =>
        val spread = CasualtiesBelowConfig.InfectionSpreadPerTick.get() * (1.0 - immuneFraction)
        val fight =
          CasualtiesBelowConfig.InfectionFightPerTick.get() * immuneFraction * fightShare
        val next = (progress + spread - fight).min(LimbStats.MaxValue)
        if (next <= 0.0) {
          stats.infectionProgress = None
          true
        } else {
          stats.infectionProgress = Some(next)
          false
        }
      case None =>
        val skinDamage = LimbStats.MaxValue - stats.skinIntegrity
        if (skinDamage < InfectionSkinDamageThreshold) return false

        val chance =
          CasualtiesBelowConfig.InfectionChancePerTick.get() * skinDamage / LimbStats.MaxValue
        if (random.nextFloat() < chance) {
          stats.infectionProgress = Some(InfectionOnsetSeed)
          true
        } else {
          false
        }
    }
  }

  /** Contagion: a limb whose infection progress is past the ramp start can seed an anatomically
    * adjacent, not-yet-infected limb (see [[BodyPart.Adjacent]] — a star with the torso as the
    * hub). The per-tick chance ramps linearly from zero at the start progress to the configured
    * maximum at the full progress. The target does not need a wound: this models septic spread
    * through the body, not wound-to-wound contact. Seeds use the same onset value as wound
    * infections, so a strong immune system visibly suppresses the spread.
    */
  private def tickContagion(player: ServerPlayer, body: BodyComponent): Unit = {
    val start = CasualtiesBelowConfig.InfectionContagionStartProgress.get()
    val full = CasualtiesBelowConfig.InfectionContagionFullProgress.get()
    val ramp = (full - start).max(1.0)
    val maxChance = CasualtiesBelowConfig.InfectionContagionMaxChancePerTick.get()

    BodyPart.values.foreach { part =>
      val stats = body.stats(part)
      stats.infectionProgress.foreach { progress =>
        val chance = maxChance * ((progress - start) / ramp).max(0.0).min(1.0)
        if (chance > 0.0 && player.getRandom.nextFloat() < chance) {
          val targets =
            BodyPart.Adjacent(part).filter(p => body.stats(p).infectionProgress.isEmpty)
          if (targets.nonEmpty) {
            val target = targets(player.getRandom.nextInt(targets.size))
            val seeded = body.stats(target)
            seeded.infectionProgress = Some(InfectionOnsetSeed)
            body.setStats(target, seeded)
            LimbInjuries.markDirty(player)
          }
        }
      }
    }
  }

  /** Consequences of an active infection. The effect strength ramps linearly from zero at
    * [[CasualtiesBelowConfig.InfectionEffectStartProgress]] to full at
    * [[CasualtiesBelowConfig.InfectionEffectFullProgress]] — a mild infection is asymptomatic, then
    * the limb starts hurting and wasting away: pain (which at full strength outruns natural decay
    * and persists until the infection recedes) and muscle decay (at full strength double the muscle
    * regrowth rate, so the limb loses muscle net).
    */
  private def tickInfectionEffects(stats: LimbStats): Unit = {
    if (stats.infectionProgress.isEmpty) return

    val start = CasualtiesBelowConfig.InfectionEffectStartProgress.get()
    val full = CasualtiesBelowConfig.InfectionEffectFullProgress.get()
    val ramp = (full - start).max(1.0)
    val severity = ((stats.infectionProgress.get - start) / ramp).max(0.0).min(1.0)
    if (severity <= 0.0) return

    stats.pain = (stats.pain + CasualtiesBelowConfig.InfectionPainPerTick.get() * severity)
      .min(LimbStats.MaxValue)
    stats.muscleHealth =
      (stats.muscleHealth - CasualtiesBelowConfig.InfectionMuscleDecayPerTick.get() * severity)
        .max(0.0)
  }

  /** Skin regrows only once the wound has clotted shut; immune health scales the rate between the
    * configured minimum multiplier (zero immune) and the full base rate (full immune).
    */
  private def tickSkinRegen(stats: LimbStats, immuneHealth: Double): Unit = {
    if (stats.externalBleedingRate > 0.0) return
    if (stats.skinIntegrity >= LimbStats.MaxValue) return

    val minMultiplier = CasualtiesBelowConfig.SkinRegenMinImmuneMultiplier.get()
    val multiplier =
      minMultiplier +
        (1.0 - minMultiplier) * immuneHealth / CasualtiesBelowConfig.MaxImmuneHealth.get()
    stats.skinIntegrity =
      (stats.skinIntegrity + SkinRegenPerTick * multiplier).min(LimbStats.MaxValue)
  }

  /** Muscle regrows regardless of bleeding (slower than skin). */
  private def tickMuscleRegen(stats: LimbStats): Unit = {
    if (stats.muscleHealth >= LimbStats.MaxValue) return

    stats.muscleHealth = (stats.muscleHealth + MuscleRegenPerTick).min(LimbStats.MaxValue)
  }

  /** Pain decays linearly at the configured rate. */
  private def tickPainDecay(stats: LimbStats): Unit = {
    if (stats.pain <= 0.0) return

    stats.pain = (stats.pain - CasualtiesBelowConfig.PainDecayPerTick.get()).max(0.0)
  }

  /** Walking strain: pain scaled by the leg's tissue damage (muscle and skin), at the configured
    * rate for the leg's condition; a rate of zero means no strain applies this tick.
    */
  private def tickWalkingStrain(stats: LimbStats, strainPainRate: Double): Unit = {
    if (strainPainRate <= 0.0) return

    val tissueDamage =
      (2.0 - stats.muscleHealth / LimbStats.MaxValue -
        stats.skinIntegrity / LimbStats.MaxValue) / 2.0
    if (tissueDamage > 0.0) {
      stats.pain = (stats.pain + strainPainRate * tissueDamage).min(LimbStats.MaxValue)
    }
  }

  /** Pain rate for walking strain on the given limb: zero unless a leg bears the walking player.
    * Fracture and dislocation are independent conditions and their configured rates stack, so a leg
    * with both suffers both rates at once.
    */
  private def walkingStrainRate(part: BodyPart, stats: LimbStats, walking: Boolean): Double = {
    if (!walking || !BodyPart.Legs.contains(part)) return 0.0

    val fractureRate: Double =
      if (stats.fractureRecoveryTicks.isDefined) {
        CasualtiesBelowConfig.FracturedWalkingPainPerTick.get()
      } else {
        0.0
      }
    val dislocationRate: Double =
      if (stats.dislocated) CasualtiesBelowConfig.DislocatedWalkingPainPerTick.get() else 0.0
    fractureRate + dislocationRate
  }

  /** Whether the player is trying to walk on the ground this tick (walking, sprinting, sneaking —
    * not swimming, flying, or being passively pushed). Uses vanilla's last client input packet
    * rather than measured displacement, so pressing a move key while stuck against a wall still
    * counts as bearing weight, while water/knockback movement without input does not.
    */
  private def isWalking(player: ServerPlayer): Boolean = {
    if (!player.onGround()) return false

    val input = player.getLastClientInput
    input.forward() || input.backward() || input.left() || input.right()
  }

  private var ticks = 0

  /** Ticks between throttled syncs of continuous changes (20 = once per second). */
  private val SyncIntervalTicks = 20

  /** Skin integrity regrown per tick once the wound is sealed (100 over ~17 min), before the immune
    * multiplier.
    */
  private val SkinRegenPerTick = 0.005

  /** Minimum skin damage (from full integrity) before a wound can get infected. */
  private val InfectionSkinDamageThreshold = 20.0

  /** Infection progress a fresh onset starts with: gives the immune system a visible suppression
    * window instead of an instant onset→cleared flicker.
    */
  private val InfectionOnsetSeed = 5.0

  /** Muscle health regrown per tick (100 over ~33 min). */
  private val MuscleRegenPerTick = 0.0025

}
