package dev.krysztal.casualtiesbelow.progression

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.RandomSource
import net.minecraft.world.effect.MobEffects

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

import dev.krysztal.casualtiesbelow.adrenaline.Adrenaline
import dev.krysztal.casualtiesbelow.api.CasualtiesBelowDamageTypes
import dev.krysztal.casualtiesbelow.api.LimbInjuries
import dev.krysztal.casualtiesbelow.api.body.BodyComponent
import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.LimbStats
import dev.krysztal.casualtiesbelow.api.body.VitalsComponent
import dev.krysztal.casualtiesbelow.bleeding.BleedingCalc
import dev.krysztal.casualtiesbelow.bleeding.TotemHemostasis
import dev.krysztal.casualtiesbelow.blood.BloodVolume
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.consciousness.Unconsciousness
import dev.krysztal.casualtiesbelow.pain.PainShock

/** Time evolution of injuries: what heals, what worsens, and what kills when left alone.
  *
  * Per server tick and per player (skipped in creative/spectator):
  *
  *   - fractures count down their recovery time and heal when it runs out; walking on a fractured
  *     or dislocated leg strains it, granting pain scaled by the leg's tissue damage (muscle and
  *     skin) at the configured rate
  *   - bleeding drains the blood volume and clots linearly; the per-limb rate is capped
  *     proportionally to the skin damage (see [[BleedingCalc.cap]]); reaching zero blood is fatal
  *     ([[CasualtiesBelowDamageTypes.BloodLoss]]), while a successful death-protection rescue
  *     restores a bounded blood reserve and temporarily reduces actual drain without closing wounds
  *     (see [[TotemHemostasis]])
  *   - exhausted vanilla air gates blood-oxygen depletion, while custom blood volume sets its
  *     carrying capacity; hypoxia pressures consciousness, while adequate oxygen permits recovery,
  *     and sustained pressure can latch the recoverable unconscious state (see
  *     [[OxygenProgression]], [[ConsciousnessProgression]])
  *   - wounds with meaningful skin damage can get infected; the immune system fights infections
  *     with its total capacity split across all infected limbs, against per-limb spread rates
  *     proportional to its complement; past a progress ramp an infection can also seed adjacent
  *     limbs (see [[tickInfection]], [[tickContagion]]), and it scales skin regrowth (see
  *     [[tickSkinRegen]])
  *   - immune health is a lifestyle stat decoupled from infection: a full stomach restores it,
  *     while hunger and active vanilla Poison drain it (see [[tickImmune]])
  *   - skin naturally regrows once a wound has clotted shut; vanilla Regeneration adds micro-repair
  *     even while bleeding and tightens the bleeding cap as the skin closes; muscle regrows
  *     regardless (slower)
  *   - pain decays linearly at the configured rate; whole-body pain above the shock threshold
  *     accumulates hidden load, which can force literal-zero unconsciousness before later
  *     permitting recovery (see [[PainShock]])
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
    // CCA's LOSSLESS_ONLY strategy copies injuries during lossless reconstruction (for example an
    // End return) and leaves ordinary death respawns at their fresh component defaults.
    ServerTickEvents.END_SERVER_TICK.register(tickServer)
  }

  private def tickServer(server: MinecraftServer): Unit = {
    ticks += 1
    val syncTick = ticks % SyncIntervalTicks == 0
    try {
      server.getPlayerList.getPlayers.forEach { player =>
        tickPlayer(player, syncTick)
      }
    } finally {
      // No player references are retained, and entries for players who disconnected during this
      // tick cannot leak into a later tick.
      StarvationProgression.discardRemaining()
    }
  }

  private def tickPlayer(player: ServerPlayer, syncTick: Boolean): Unit = {
    if (!player.isAlive) {
      StarvationProgression.discard(player)
      Adrenaline.discard(player)
      return
    }
    if (player.isCreative || player.isSpectator) {
      StarvationProgression.discard(player)
      // A command or another mod can change modes after an accepted survival hit but before this
      // END_SERVER_TICK pass. Physiology remains frozen in creative/spectator, while the already
      // committed public reserve still needs its one owner sync.
      if (Adrenaline.consumeDirty(player)) {
        CasualtiesBelowComponents.Vitals.sync(player)
      }
      return
    }

    val body = CasualtiesBelowComponents.Body.get(player)
    val vitals = CasualtiesBelowComponents.Vitals.get(player)
    val walking = isWalking(player)
    val regenerationMultiplier =
      Option(player.getEffect(MobEffects.REGENERATION)).fold(0.0)(_.getAmplifier + 1.0)
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
        regenerationMultiplier,
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

    // A fresh AFTER_DAMAGE stimulus carries a one-tick sentinel, so aging here preserves its full
    // amount for this tick's shock decision. Later decay can contract the effective threshold and
    // collapse Deferred in this same pass. All damage grants still ship in this one vitals sync.
    var vitalsChanged = Adrenaline.consumeDirty(player)
    vitalsChanged = Adrenaline.tick(vitals) || vitalsChanged

    // Pain shock reads the fully updated per-limb pains and current adrenaline. Awake-load integer
    // crossings request owner-only warning interpolation syncs; phase transitions sync at once.
    vitalsChanged = PainShock.tick(body, vitals) || vitalsChanged
    vitalsChanged = tickSepsis(vitals, infectionLoad) || vitalsChanged
    vitalsChanged = tickImmune(vitals, player) || vitalsChanged

    // Sepsis compresses the effective blood cap; well-fed players regenerate blood up to it.
    // Blood over the cap is lost outright: surviving sepsis leaves the body drained, and
    // recovery means eating well.
    val maxBlood = BloodVolume.effectiveMaximum(vitals)
    vitalsChanged = BloodVolume.clamp(vitals, maxBlood) || vitalsChanged
    if (player.getFoodData.getFoodLevel >= CasualtiesBelowConfig.FedFoodLevelThreshold.get()) {
      val regenerated = BloodVolume.restore(
        vitals,
        CasualtiesBelowConfig.FedBloodRegenPerTick.get(),
        maxBlood
      )
      vitalsChanged = regenerated > 0.0 || vitalsChanged
    }

    // Accepted vanilla starvation pulses are translated first. The final blood check below keeps
    // source priority deterministic if bleeding also applies in this tick.
    val starvation = StarvationProgression.consume(player, vitals, maxBlood)
    vitalsChanged = starvation.changed || vitalsChanged

    if (totalBleeding > 0.0) {
      val actualBleeding = totalBleeding * TotemHemostasis.bleedingMultiplier(vitals)
      if (actualBleeding > 0.0) {
        val drained = BloodVolume.drain(vitals, actualBleeding, maxBlood)
        vitalsChanged = drained > 0.0 || vitalsChanged
      }
    }
    // The timer is hidden client-side state: advancing it does not force an extra sync. Any blood
    // change already syncs through vitalsChanged, while persistence always writes the live value.
    TotemHemostasis.tick(vitals)

    // Zero blood is fatal before oxygen can drive consciousness to its floor and latch the
    // recoverable unconscious state. Blood-loss death protection restores blood synchronously in
    // the vanilla totem path; an unrescued player remains at zero and dies normally.
    if (vitals.bloodVolume <= 0.0) {
      val fatal =
        if (maxBlood <= 0.0) {
          CasualtiesBelowDamageTypes.sepsis(player.level())
        } else if (starvation.reachedZero) {
          CasualtiesBelowDamageTypes.starvation(player.level())
        } else {
          CasualtiesBelowDamageTypes.bloodLoss(player.level())
        }
      player.hurtServer(player.level(), fatal, Float.MaxValue)
      if (vitalsChanged) {
        CasualtiesBelowComponents.Vitals.sync(player)
      }
      return
    }

    // Read vanilla's already-updated air supply after the blood changes above: blood volume sets
    // oxygen capacity, while fully exhausted air gates depletion. Consciousness progression then
    // consumes that reserve and owns both the scalar and the recoverable unconscious latch.
    val oxygen = OxygenProgression.tick(player, vitals)
    vitalsChanged = oxygen.changed || vitalsChanged
    vitalsChanged = ConsciousnessProgression.tick(player, vitals) || vitalsChanged

    // Terminal exposure starts only after oxygen and consciousness consumed this tick's breathing
    // state. A successful death-protection hit restores physiology synchronously; either way this
    // player's progression returns immediately after the fatal call.
    if (HypoxiaProgression.tick(vitals, oxygen.breathingBlocked)) {
      player.hurtServer(
        player.level(),
        CasualtiesBelowDamageTypes.hypoxia(player.level()),
        Float.MaxValue
      )
      if (vitalsChanged) {
        CasualtiesBelowComponents.Vitals.sync(player)
      }
      return
    }

    vitalsChanged = PainShock.finishRecovery(vitals) || vitalsChanged
    Unconsciousness.tickMovementRestriction(player)

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

  /** Immune health is a lifestyle stat deliberately decoupled from infection load. Being well-fed
    * restores it slowly and hunger drains it (thresholds mirror vanilla's regeneration/sprinting
    * cutoffs). Active vanilla Poison adds a continuous drain scaled linearly by effect level,
    * independently of whether a poison damage pulse lands. Returns whether the value changed.
    */
  private def tickImmune(vitals: VitalsComponent, player: ServerPlayer): Boolean = {
    val food = player.getFoodData.getFoodLevel
    val foodDelta: Double =
      if (food >= CasualtiesBelowConfig.FedFoodLevelThreshold.get().intValue) {
        CasualtiesBelowConfig.FedImmuneRegenPerTick.get()
      } else if (food < CasualtiesBelowConfig.HungryFoodLevelThreshold.get().intValue) {
        -CasualtiesBelowConfig.HungryImmuneDrainPerTick.get()
      } else {
        0.0
      }

    val poisonDrain = Option(player.getEffect(MobEffects.POISON)).fold(0.0) { effect =>
      CasualtiesBelowConfig.PoisonImmuneDrainPerTick.get() * (effect.getAmplifier + 1)
    }
    val delta = foodDelta - poisonDrain
    if (delta == 0.0) return false

    val next =
      (vitals.immuneHealth + delta).max(0.0).min(CasualtiesBelowConfig.MaxImmuneHealth.get())
    if (next == vitals.immuneHealth) return false

    vitals.immuneHealth = next
    true
  }

  /** One tick of evolution for one limb, mutating the given copy in place. `strainPainRate` is the
    * walking-strain pain rate when the limb is a fractured/dislocated leg currently bearing the
    * walking player, zero otherwise. `immuneHealth` modulates infection spread and natural skin
    * regrowth; `regenerationMultiplier` drives the independent vanilla Regeneration micro-repair.
    * Returns whether a discrete transition occurred (fracture healed, bleeding stopped, infection
    * started or cleared).
    *
    * Ordering matters: bleeding clots before natural skin regrowth is considered, so a wound that
    * seals this tick starts regrowing skin immediately. Regeneration then repairs skin even if the
    * wound remains open and reconciles its tighter bleeding cap in the same tick.
    */
  private def tickLimb(
      stats: LimbStats,
      strainPainRate: Double,
      immuneHealth: Double,
      fightShare: Double,
      regenerationMultiplier: Double,
      random: RandomSource
  ): Boolean = {

    given givenStats: LimbStats = stats

    val fractureHealed = tickFracture()
    val bleedingStopped = tickBleeding()
    val infectionTransition = tickInfection(immuneHealth, fightShare, random)

    tickInfectionEffects()
    tickSkinRegen(immuneHealth)
    val regenerationTransition =
      tickRegenerationSkin(regenerationMultiplier)
    tickMuscleRegen()
    tickPainDecay()
    tickWalkingStrain(strainPainRate)

    fractureHealed || bleedingStopped || regenerationTransition || infectionTransition
  }

  /** Counts down the fracture recovery time; returns true when the fracture healed this tick. */
  private def tickFracture()(using stats: LimbStats): Boolean = {
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
  private def tickBleeding()(using stats: LimbStats): Boolean = {
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
      immuneHealth: Double,
      fightShare: Double,
      random: RandomSource
  )(using stats: LimbStats): Boolean = {
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
  private def tickInfectionEffects()(using stats: LimbStats): Unit = {
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
  private def tickSkinRegen(immuneHealth: Double)(using stats: LimbStats): Unit = {
    if (stats.externalBleedingRate > 0.0) return
    if (stats.skinIntegrity >= LimbStats.MaxValue) return

    val minMultiplier = CasualtiesBelowConfig.SkinRegenMinImmuneMultiplier.get()
    val multiplier =
      minMultiplier +
        (1.0 - minMultiplier) * immuneHealth / CasualtiesBelowConfig.MaxImmuneHealth.get()
    stats.skinIntegrity =
      (stats.skinIntegrity + SkinRegenPerTick * multiplier).min(LimbStats.MaxValue)
  }

  /** Vanilla Regeneration provides micro skin repair on every damaged limb, even while bleeding.
    * Raising skin integrity immediately lowers the limb's allowed bleeding cap. Returns whether the
    * skin reached full integrity or the cap reconciliation stopped an active bleed, so either
    * discrete transition syncs immediately.
    */
  private def tickRegenerationSkin(multiplier: Double)(using stats: LimbStats): Boolean = {
    if (multiplier <= 0.0 || stats.skinIntegrity >= LimbStats.MaxValue) return false

    val restore = CasualtiesBelowConfig.RegenerationSkinRestorePerTick.get() * multiplier
    if (restore <= 0.0) return false

    val previousSkin = stats.skinIntegrity
    val wasBleeding = stats.externalBleedingRate > 0.0
    stats.skinIntegrity = (previousSkin + restore).min(LimbStats.MaxValue)
    stats.externalBleedingRate =
      stats.externalBleedingRate.min(BleedingCalc.cap(stats.skinIntegrity))

    val reachedFullSkin =
      previousSkin < LimbStats.MaxValue && stats.skinIntegrity >= LimbStats.MaxValue
    val stoppedBleeding = wasBleeding && stats.externalBleedingRate <= 0.0
    reachedFullSkin || stoppedBleeding
  }

  /** Muscle regrows regardless of bleeding (slower than skin). */
  private def tickMuscleRegen()(using stats: LimbStats): Unit = {
    if (stats.muscleHealth >= LimbStats.MaxValue) return

    stats.muscleHealth = (stats.muscleHealth + MuscleRegenPerTick).min(LimbStats.MaxValue)
  }

  /** Pain decays linearly at the configured rate. */
  private def tickPainDecay()(using stats: LimbStats): Unit = {
    if (stats.pain <= 0.0) return

    stats.pain = (stats.pain - CasualtiesBelowConfig.PainDecayPerTick.get()).max(0.0)
  }

  /** Walking strain: pain scaled by the leg's tissue damage (muscle and skin), at the configured
    * rate for the leg's condition; a rate of zero means no strain applies this tick.
    */
  private def tickWalkingStrain(strainPainRate: Double)(using stats: LimbStats): Unit = {
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
