package dev.krysztal.casualtiesbelow.item

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.player.Player

import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.component.MutableLimbState
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extension.Prelude.*

/** Server-authoritative settlement of batched injection progress reported by the injection screen.
  *
  * Settlement semantics (side-effect model): the pushed amount always lands as dose proportionally
  * — `storedDose * appliedDroplets / currentDroplets` — while push speed only produces side effects
  * (discomfort + injection-site pain), linear in speed and amount: pushing one full syringe at
  * maximum speed yields the configured full-dose side-effect caps.
  *
  * Trust model: the client reports outcomes, the server clamps rather than reconstructs the
  * minigame. Reported droplets are clamped to the syringe's actual remainder and speed to [0, 1],
  * then the remainder is written back scaled down proportionally; the syringe is consumed only when
  * emptied (never in creative). Each batch additionally carries the identity of the syringe the
  * screen session belongs to (liquid + the droplets the stack should currently hold); a batch that
  * does not match the held stack is dropped, so a swapped or otherwise desynced stack can never
  * receive another syringe's settlement.
  */
private[casualtiesbelow] object InjectionSettlement {

  /** Applies one batch to the syringe held in `hand`. No-ops when the held stack is not a filled
    * syringe, when its identity differs from `liquid`/`expectedDroplets` (the droplets the stack
    * should hold once all previously sent batches have settled), or when the batch carries nothing.
    */
  def applyBatch(
      player: ServerPlayer,
      hand: InteractionHand,
      liquid: Identifier,
      expectedDroplets: Long,
      requestedDroplets: Long,
      averageSpeed: Double
  ): Unit = {
    val stack = player.getItemInHand(hand)
    if (stack.isEmpty) return
    val contents = stack.syringeContents match {
      case Some(contents) => contents
      case None           => return
    }
    // The client session seeds from a contents snapshot; settle only against that same syringe.
    // A mid-session stack swap (or a partially clamped earlier batch) breaks the identity and
    // fails closed: the batch is ignored rather than settled against the wrong stack.
    if (contents.liquid != liquid || contents.droplets != expectedDroplets) return
    val applied = clampDroplets(requestedDroplets, contents.droplets)
    if (applied <= 0L) return
    val speed = clampSpeed(averageSpeed)

    val doseDelta = doseFor(contents.opioidDose, applied, contents.droplets)
    val discomfort = sideEffect(
      Consts.Injection.FullDoseSideEffectDiscomfort,
      speed,
      applied,
      LiquidContents.AmpouleDroplets
    )
    val pain = sideEffect(
      Consts.Injection.FullDoseSideEffectPain,
      speed,
      applied,
      LiquidContents.AmpouleDroplets
    )

    val vitals = player.vitals
    // Dirty needle: contamination scales with the player's dirtiness and the pushed fraction,
    // never with speed — a careful push through dirty skin still infects.
    val infectionSeed = allowedInfectionSeed(
      infectionSeedFor(
        Consts.Dirtiness.InjectionSeedAtMax,
        vitals.dirtiness,
        Consts.Dirtiness.MaxValue,
        applied,
        LiquidContents.AmpouleDroplets
      ),
      CasualtiesBelowConfig.diseaseHygiene.infectionEnabled.get(),
      player.body.stats(injectedPart(player, hand)).infectionProgress.isPresent
    )
    if (doseDelta != 0.0) {
      VitalsMutations.setOpioidLevel(vitals, vitals.opioidLevel + doseDelta)
    }
    if (discomfort != 0.0) {
      VitalsMutations.setDiscomfort(vitals, vitals.discomfort + discomfort)
    }
    VitalsMutations.syncNow(player)
    if (pain != 0.0 || infectionSeed > 0.0) {
      BodyMutations.mutate(player, injectedPart(player, hand), markDirty = true) { state =>
        if (pain != 0.0) {
          state.pain = state.pain + pain
        }
        if (infectionSeed > 0.0) {
          val progress = state.infectionProgress.getOrElse(0.0)
          state.infectionProgress = Some((progress + infectionSeed).min(MutableLimbState.MaxValue))
        }
      }
    }

    remainder(contents, applied) match {
      case Some(remaining) =>
        stack.withSyringeContents(remaining)
      case None =>
        player.awardStat(Stats.ITEM_USED.get(stack.getItem))
        if (!player.hasInfiniteMaterials()) stack.consume(1, player)
    }
  }

  /** Disabling infections prevents a dirty needle from creating a new infection, but never removes
    * or freezes contamination of a limb that is already infected.
    */
  private[item] def allowedInfectionSeed(
      seed: Double,
      infectionEnabled: Boolean,
      alreadyInfected: Boolean
  ): Double =
    if (infectionEnabled || alreadyInfected) seed else 0.0

  /** Reported droplets clamped to the amount actually present. */
  private[item] def clampDroplets(requested: Long, remaining: Long): Long =
    requested.max(0L).min(remaining.max(0L))

  /** Reported speed fraction clamped to [0, 1]; garbage input counts as stationary. */
  private[item] def clampSpeed(speed: Double): Double = {
    if (speed.isNaN || speed <= 0.0) 0.0 else if (speed >= 1.0) 1.0 else speed
  }

  /** Dose landing for `appliedDroplets` of a syringe currently holding `currentDroplets`. */
  private[item] def doseFor(
      storedDose: Double,
      appliedDroplets: Long,
      currentDroplets: Long
  ): Double = {
    if (currentDroplets <= 0L || storedDose <= 0.0) 0.0
    else {
      val fraction = (appliedDroplets.toDouble / currentDroplets.toDouble).max(0.0).min(1.0)
      storedDose * fraction
    }
  }

  /** Side-effect amount for a batch: `fullDoseCap` is what pushing a whole syringe at maximum speed
    * yields; scales linearly with speed and pushed amount.
    */
  private[item] def sideEffect(
      fullDoseCap: Double,
      speedFraction: Double,
      appliedDroplets: Long,
      fullDroplets: Long
  ): Double = {
    if (fullDroplets <= 0L || fullDoseCap <= 0.0) 0.0
    else {
      val amountFraction =
        (appliedDroplets.toDouble / fullDroplets.toDouble).max(0.0).min(1.0)
      fullDoseCap * clampSpeed(speedFraction) * amountFraction
    }
  }

  /** Infection progress seeded into the injected limb by a batch: `maxSeed` is what one full
    * syringe at maximum dirtiness yields; scales linearly with dirtiness (clamped to the axis) and
    * with the pushed fraction, so batched settlement sums to the same total regardless of batching.
    */
  private[item] def infectionSeedFor(
      maxSeed: Double,
      dirtiness: Double,
      maxDirtiness: Double,
      appliedDroplets: Long,
      fullDroplets: Long
  ): Double = {
    if (fullDroplets <= 0L || maxSeed <= 0.0 || maxDirtiness <= 0.0) return 0.0
    val pushedFraction =
      (appliedDroplets.toDouble / fullDroplets.toDouble).max(0.0).min(1.0)
    val dirtinessFraction = (dirtiness / maxDirtiness).max(0.0).min(1.0)
    maxSeed * dirtinessFraction * pushedFraction
  }

  /** The syringe contents after `appliedDroplets` leave: droplets and dose scale down
    * proportionally; None when the syringe is emptied.
    */
  private[item] def remainder(
      contents: SyringeContents,
      appliedDroplets: Long
  ): Option[SyringeContents] = {
    val left = contents.droplets - clampDroplets(appliedDroplets, contents.droplets)
    if (left <= 0L) None
    else {
      Some(
        contents.copy(
          droplets = left,
          opioidDose = doseFor(contents.opioidDose, left, contents.droplets)
        )
      )
    }
  }

  /** The injection goes into the arm not holding the syringe (main-hand syringe pricks the off-hand
    * arm).
    */
  private def injectedPart(player: Player, hand: InteractionHand): BodyPart = {
    val syringeInRightHand =
      (hand == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.RIGHT)
    if (syringeInRightHand) BodyPart.ArmLeft else BodyPart.ArmRight
  }
}
