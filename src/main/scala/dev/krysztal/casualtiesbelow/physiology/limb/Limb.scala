package dev.krysztal.casualtiesbelow.physiology.limb

import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.RandomSource
import net.minecraft.world.effect.MobEffects

import dev.krysztal.casualtiesbelow.api.body.limb.BodyComponent
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.component.BodyTopology
import dev.krysztal.casualtiesbelow.component.MutableLimbState
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*
import dev.krysztal.casualtiesbelow.physiology.dirtiness.Dirtiness
import dev.krysztal.casualtiesbelow.physiology.opioid.OpioidEffects
import dev.krysztal.casualtiesbelow.physiology.opioid.OpioidWithdrawal

/** One evolution pass over the body's limbs: fractures count down, wounds clot, infections onset,
  * spread and get fought, skin and muscle regrow, pain decays, and walking on a broken leg strains
  * it. Discrete transitions (healed / stopped / infected / cleared) are reported so callers can
  * sync immediately.
  *
  * Cross-vital dependencies, declared: opioid withdrawal hyperalgesia scales every positive pain
  * grant; infection fight capacity reads immune health; infection onset and skin regrowth read
  * dirtiness; vanilla Regeneration drives independent micro-repair. Bleeding totals feed the
  * circulation pass; infection load feeds the sepsis pass.
  */
private[casualtiesbelow] object Limb {

  /** @param totalBleeding
    *   the sum of every limb's active external bleeding rate this tick (drives the circulation
    *   pass)
    * @param infectionLoad
    *   the sum of every limb's infection progress (drives sepsis)
    */
  final case class Outcome(totalBleeding: Double, infectionLoad: Double)

  /** Per-pass configuration reads, hoisted out of the per-limb loop. */
  private final case class PassConfig(
      infectionEnabled: Boolean,
      woundRiskMultiplier: Double,
      clottingSpeedMultiplier: Double,
      naturalHealingMultiplier: Double
  )

  /** One evolution pass over every limb, committing changed limb states through [[BodyMutations]].
    * `syncTick` marks the throttled sync boundary: continuous changes only mark the body dirty
    * there, while discrete transitions sync immediately.
    */
  def tick(
      player: ServerPlayer,
      body: BodyComponent,
      vitals: VitalsComponentImpl,
      syncTick: Boolean
  ): Outcome = {

    val walking = isWalking(player)

    // Per-pass config and vitals reads, hoisted out of the per-limb loop.
    given cfg: PassConfig = PassConfig(
      CasualtiesBelowConfig.diseaseHygiene.infectionEnabled.get(),
      CasualtiesBelowConfig.diseaseHygiene.woundInfectionRiskMultiplier.get(),
      CasualtiesBelowConfig.injurySurvival.clottingSpeedMultiplier.get(),
      CasualtiesBelowConfig.injurySurvival.naturalHealingMultiplier.get()
    )
    val immuneFraction = vitals.infection.immuneHealth / Consts.Vitals.MaxImmuneHealth

    val withdrawalPainMultiplier = OpioidWithdrawal.painGrantMultiplier(vitals)
    val opioidPainDrain =
      OpioidEffects.painDrainPerTick(vitals.opioidLevel, vitals.opioidDependence)
    val regenerationMultiplier =
      Option(player.getEffect(MobEffects.REGENERATION)).fold(0.0)(_.getAmplifier + 1.0)
    var totalBleeding = 0.0

    // The immune system splits its fight capacity across all infected limbs: one infection is
    // containable, several at once overwhelm a marginal immune system.
    val infectedCount = BodyPart.values.count(p => body.stats(p).infectionProgress.isPresent)
    val fightShare = 1.0 / infectedCount.max(1)
    var infectionLoad = 0.0

    BodyPart.values.foreach { part =>
      val current = MutableLimbState.from(body.stats(part))
      val updated = current.copy()
      val discrete = tickLimb(
        updated,
        walkingStrainRate(part, current, walking),
        withdrawalPainMultiplier,
        opioidPainDrain,
        immuneFraction,
        vitals.dirtiness,
        fightShare,
        regenerationMultiplier,
        player.getRandom
      )
      totalBleeding += updated.externalBleedingRate
      infectionLoad += updated.infectionProgress.getOrElse(0.0)
      if (updated != current) {
        BodyMutations.replace(player, part, updated, markDirty = discrete || syncTick)
      }
    }

    tickContagion(player, body)

    Outcome(totalBleeding, infectionLoad)
  }

  /** One tick of evolution for one limb, mutating the given copy in place. `strainPainRate` is the
    * walking-strain pain rate when the limb is a fractured/dislocated leg currently bearing the
    * walking player, zero otherwise. `painGrantMultiplier` applies systemic hyperalgesia to all
    * positive pain grants. `immuneFraction` modulates infection spread and natural skin regrowth;
    * `dirtiness` modulates infection onset and skin regrowth; `regenerationMultiplier` drives the
    * independent vanilla Regeneration micro-repair. Returns whether a discrete transition occurred
    * (fracture healed, bleeding stopped, infection started or cleared).
    *
    * Ordering matters: bleeding clots before natural skin regrowth is considered, so a wound that
    * seals this tick starts regrowing skin immediately. Regeneration then repairs skin even if the
    * wound remains open and reconciles its tighter bleeding cap in the same tick.
    */
  private def tickLimb(
      stats: MutableLimbState,
      strainPainRate: Double,
      painGrantMultiplier: Double,
      opioidPainDrain: Double,
      immuneFraction: Double,
      dirtiness: Double,
      fightShare: Double,
      regenerationMultiplier: Double,
      random: RandomSource
  )(using cfg: PassConfig): Boolean = {

    given givenStats: MutableLimbState = stats

    val fractureHealed = tickFracture()
    val bleedingStopped = tickBleeding()
    val infectionTransition = tickInfection(immuneFraction, dirtiness, fightShare, random)

    tickInfectionEffects(painGrantMultiplier)
    tickSkinRegen(immuneFraction, dirtiness)
    val regenerationTransition =
      tickRegenerationSkin(regenerationMultiplier)
    tickMuscleRegen()
    tickPainDecay(opioidPainDrain)
    tickWalkingStrain(strainPainRate * painGrantMultiplier)

    fractureHealed || bleedingStopped || regenerationTransition || infectionTransition
  }

  /** Counts down the fracture recovery time; returns true when the fracture healed this tick. */
  private def tickFracture()(using stats: MutableLimbState): Boolean = {
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
  private def tickBleeding()(using stats: MutableLimbState, cfg: PassConfig): Boolean = {
    if (stats.externalBleedingRate <= 0.0) return false

    val capped = stats.externalBleedingRate.min(BleedingCalc.cap(stats.skinIntegrity))
    val clotted =
      (capped - Consts.Bleeding.ClottingRatePerTick * cfg.clottingSpeedMultiplier).max(0.0)
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
    * 200 (see [[Consts.Infection.immuneBreakEven]]). Returns true on the discrete transitions
    * (onset, cleared).
    */
  private def tickInfection(
      immuneFraction: Double,
      dirtiness: Double,
      fightShare: Double,
      random: RandomSource
  )(using stats: MutableLimbState, cfg: PassConfig): Boolean = {
    stats.infectionProgress match {
      case Some(progress) =>
        val next = LimbCalc.nextInfectionProgress(
          progress,
          Consts.Infection.InfectionSpreadPerTick,
          Consts.Infection.InfectionFightPerTick,
          immuneFraction,
          fightShare,
          MutableLimbState.MaxValue
        )
        if (next <= 0.0) {
          stats.infectionProgress = None
          true
        } else {
          stats.infectionProgress = Some(next)
          false
        }
      case None =>
        if (!cfg.infectionEnabled) return false
        val skinDamage = MutableLimbState.MaxValue - stats.skinIntegrity
        if (skinDamage < InfectionSkinDamageThreshold) return false

        val chance = LimbCalc.woundOnsetChance(
          skinDamage,
          dirtiness,
          cfg.woundRiskMultiplier,
          Consts.Infection.InfectionChancePerTick,
          MutableLimbState.MaxValue,
          Consts.Dirtiness.MaxValue,
          Consts.Dirtiness.InfectionChanceMultiplierAtMax
        )
        if (random.nextFloat() < chance) {
          stats.infectionProgress = Some(InfectionOnsetSeed)
          true
        } else {
          false
        }
    }
  }

  /** Contagion: a limb whose infection progress is past the ramp start can seed an anatomically
    * adjacent, not-yet-infected limb (a star with the torso as the hub). The per-tick chance ramps
    * linearly from zero at the start progress to the configured maximum at the full progress. The
    * target does not need a wound: this models septic spread through the body, not wound-to-wound
    * contact. Seeds use the same onset value as wound infections, so a strong immune system visibly
    * suppresses the spread.
    */
  private def tickContagion(player: ServerPlayer, body: BodyComponent)(using
      cfg: PassConfig
  ): Unit = {
    if (!cfg.infectionEnabled) return
    val start = Consts.Infection.InfectionContagionStartProgress
    val full = Consts.Infection.InfectionContagionFullProgress
    val maxChance = Consts.Infection.InfectionContagionMaxChancePerTick

    BodyPart.values.foreach { part =>
      val stats = body.stats(part)
      if (stats.infectionProgress.isPresent) {
        val progress = stats.infectionProgress.getAsDouble
        val chance = maxChance * progress.ramp01(start, full)
        if (chance > 0.0 && player.getRandom.nextFloat() < chance) {
          val targets =
            BodyTopology.Adjacent(part).filter(p => !body.stats(p).infectionProgress.isPresent)
          if (targets.nonEmpty) {
            val target = targets(player.getRandom.nextInt(targets.size))
            BodyMutations.mutate(player, target) { state =>
              state.infectionProgress = Some(InfectionOnsetSeed)
            }
          }
        }
      }
    }
  }

  /** Consequences of an active infection. The effect strength ramps linearly from zero at
    * [[Consts.Infection.InfectionEffectStartProgress]] to full at
    * [[Consts.Infection.InfectionEffectFullProgress]] — a mild infection is asymptomatic, then the
    * limb starts hurting and wasting away: pain (which at full strength outruns natural decay and
    * persists until the infection recedes) and muscle decay (at full strength double the muscle
    * regrowth rate, so the limb loses muscle net).
    */
  private def tickInfectionEffects(painGrantMultiplier: Double)(using
      stats: MutableLimbState
  ): Unit = {
    if (stats.infectionProgress.isEmpty) return

    val severity = stats.infectionProgress.get.ramp01(
      Consts.Infection.InfectionEffectStartProgress,
      Consts.Infection.InfectionEffectFullProgress
    )
    if (severity <= 0.0) return

    stats.pain = LimbCalc.infectionPainAfterGrant(
      stats.pain,
      Consts.Infection.InfectionPainPerTick * severity,
      painGrantMultiplier,
      MutableLimbState.MaxValue
    )
    stats.muscleHealth =
      (stats.muscleHealth - Consts.Infection.InfectionMuscleDecayPerTick * severity)
        .max(0.0)
  }

  /** Skin regrows only once the wound has clotted shut; immune health scales the rate between the
    * fixed minimum multiplier (zero immune) and the full base rate (full immune), and dirtiness
    * applies its own linear ramp on top. The player-facing natural healing multiplier applies last.
    */
  private def tickSkinRegen(immuneFraction: Double, dirtiness: Double)(using
      stats: MutableLimbState,
      cfg: PassConfig
  ): Unit = {
    if (stats.externalBleedingRate > 0.0) return
    if (stats.skinIntegrity >= MutableLimbState.MaxValue) return

    val immuneMultiplier = LimbCalc.immuneScaledMultiplier(
      Consts.Regeneration.SkinRegenMinImmuneMultiplier,
      immuneFraction
    )
    val multiplier =
      immuneMultiplier * Dirtiness.skinRegenMultiplier(
        dirtiness,
        Consts.Dirtiness.MaxValue,
        Consts.Regeneration.SkinRegenMinDirtinessMultiplier
      )
    stats.skinIntegrity =
      (stats.skinIntegrity + SkinRegenPerTick * multiplier * cfg.naturalHealingMultiplier)
        .min(MutableLimbState.MaxValue)
  }

  /** Vanilla Regeneration provides micro skin repair on every damaged limb, even while bleeding.
    * Raising skin integrity immediately lowers the limb's allowed bleeding cap. Returns whether the
    * skin reached full integrity or the cap reconciliation stopped an active bleed, so either
    * discrete transition syncs immediately.
    */
  private def tickRegenerationSkin(multiplier: Double)(using stats: MutableLimbState): Boolean = {
    if (multiplier <= 0.0 || stats.skinIntegrity >= MutableLimbState.MaxValue) return false

    val restore = Consts.Regeneration.SkinRestorePerTick * multiplier
    if (restore <= 0.0) return false

    val previousSkin = stats.skinIntegrity
    val wasBleeding = stats.externalBleedingRate > 0.0
    stats.skinIntegrity = (previousSkin + restore).min(MutableLimbState.MaxValue)
    stats.externalBleedingRate =
      stats.externalBleedingRate.min(BleedingCalc.cap(stats.skinIntegrity))

    val reachedFullSkin =
      previousSkin < MutableLimbState.MaxValue && stats.skinIntegrity >= MutableLimbState.MaxValue
    val stoppedBleeding = wasBleeding && stats.externalBleedingRate <= 0.0
    reachedFullSkin || stoppedBleeding
  }

  /** Muscle regrows regardless of bleeding (slower than skin). */
  private def tickMuscleRegen()(using stats: MutableLimbState, cfg: PassConfig): Unit = {
    if (stats.muscleHealth >= MutableLimbState.MaxValue) return

    stats.muscleHealth = (stats.muscleHealth + MuscleRegenPerTick * cfg.naturalHealingMultiplier)
      .min(MutableLimbState.MaxValue)
  }

  /** Pain decays linearly at the configured rate, accelerated by effective opioid exposure: the
    * drain consumes stored pain, so relief persists after the drug itself fades.
    */
  private def tickPainDecay(opioidPainDrain: Double)(using stats: MutableLimbState): Unit = {
    if (stats.pain <= 0.0) return

    stats.pain = (stats.pain
      - Consts.Pain.PainDecayPerTick
      - opioidPainDrain).max(0.0)
  }

  /** Walking strain: pain scaled by the leg's tissue damage (muscle and skin), at the configured
    * rate for the leg's condition; a rate of zero means no strain applies this tick.
    */
  private def tickWalkingStrain(strainPainRate: Double)(using stats: MutableLimbState): Unit = {
    if (strainPainRate <= 0.0) return

    val tissueDamage = LimbCalc.tissueDamageFraction(
      stats.muscleHealth,
      stats.skinIntegrity,
      MutableLimbState.MaxValue
    )
    if (tissueDamage > 0.0) {
      stats.pain = (stats.pain + strainPainRate * tissueDamage).min(MutableLimbState.MaxValue)
    }
  }

  /** Pain rate for walking strain on the given limb: zero unless a leg bears the walking player.
    * Fracture and dislocation are independent conditions and their configured rates stack, so a leg
    * with both suffers both rates at once.
    */
  private def walkingStrainRate(
      part: BodyPart,
      stats: MutableLimbState,
      walking: Boolean
  ): Double = {
    LimbCalc.walkingStrainRate(
      walking && BodyTopology.Legs.contains(part),
      stats.fractureRecoveryTicks.isDefined,
      stats.dislocated,
      Consts.Pain.FracturedWalkingPainPerTick,
      Consts.Pain.DislocatedWalkingPainPerTick
    )
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
