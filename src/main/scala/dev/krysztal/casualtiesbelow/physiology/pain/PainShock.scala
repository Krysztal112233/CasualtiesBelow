package dev.krysztal.casualtiesbelow.physiology.pain

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.body.limb.BodyComponent
import dev.krysztal.casualtiesbelow.api.body.vitals.PainShockStage
import dev.krysztal.casualtiesbelow.api.body.vitals.ShockSnapshot
import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.api.event.PainShockStageChangedCallback
import dev.krysztal.casualtiesbelow.api.event.PainShockStageChangedContext
import dev.krysztal.casualtiesbelow.api.event.PhysiologyChangeCause
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.physiology.adrenaline.Adrenaline

/** Hidden pain-shock load and its discrete collapse/recovery lifecycle.
  *
  * Whole-body pain is the only source. Pain at or below the configured start threshold lets load
  * recover; pain above it accumulates load linearly until the configured full-rate threshold, then
  * remains at the maximum rate. Crossing the collapse threshold upward starts an episode, while
  * adrenaline temporarily raises that collapse threshold. A crossing held above the base threshold
  * enters [[PainShockStage.Deferred]] without changing consciousness; returning below the base
  * threshold cancels the pending collapse. Direction after an actual collapse is encoded by
  * [[PainShockStage]] rather than by separate entry and exit numbers.
  *
  * Load remains hidden from numeric UI, but crossing an integer while awake requests an owner-only
  * component sync for smooth, server-authoritative peripheral warning visuals. Stage transitions
  * still sync immediately because they alter consciousness behavior.
  */
object PainShock {
  val MaxLoad: Double = 100.0

  /** Advances load from current whole-body pain and reconciles the shock phase. Returns whether the
    * phase changed or stable load crossed an integer boundary and therefore requires a vitals sync.
    */
  def tick(
      player: ServerPlayer,
      body: BodyComponent,
      vitals: VitalsComponentImpl
  ): Boolean = {
    val previousLoad = normalizeLoad(vitals.shock.load)
    val previousStage = vitals.shock.stage
    val nextLoad = nextLoadFromPain(previousLoad, PainCalc.total(body))
    applyLoad(player, vitals, previousLoad, nextLoad, PhysiologyChangeCause.Progression) ||
    (isWarningStage(previousStage) && crossedInteger(previousLoad, nextLoad))
  }

  /** Clears the recovery phase after the centralized consciousness authority actually wakes the
    * player, clamping residual load to the configured post-wake cap. Returns whether the phase
    * changed.
    */
  def finishRecovery(player: ServerPlayer, vitals: VitalsComponentImpl): Boolean = {
    if (vitals.shock.stage != PainShockStage.Recovering || vitals.consciousness.unconscious) {
      return false
    }

    val wakeLoadCap =
      Consts.Pain.ShockWakeLoadCap.max(0.0).min(MaxLoad)
    val retainedLoad = normalizeLoad(vitals.shock.load).min(wakeLoadCap)
    val previousLoad = normalizeLoad(vitals.shock.load)
    val previousStage = vitals.shock.stage
    VitalsMutations.applyShockState(
      vitals,
      ShockSnapshot(retainedLoad, PainShockStage.Stable)
    )
    emitStageChange(
      player,
      previousStage,
      PainShockStage.Stable,
      previousLoad,
      retainedLoad,
      PhysiologyChangeCause.Recovery
    )
    true
  }

  /** Authoritative debug edit. The old and new load values preserve the same directional threshold
    * semantics as physiological progression.
    */
  def applyAuthoritativeEdit(
      player: ServerPlayer,
      vitals: VitalsComponentImpl,
      requestedLoad: Double
  ): Boolean = {
    val previousLoad = normalizeLoad(vitals.shock.load)
    applyLoad(
      player,
      vitals,
      previousLoad,
      normalizeLoad(requestedLoad),
      PhysiologyChangeCause.AdminEdit
    )
  }

  /** Reconciles a threshold-only change (for example an admin adrenaline edit) without inventing a
    * load direction. Collapsed/recovering episodes retain their existing direction memory.
    */
  def reconcileAfterAdrenalineEdit(
      player: ServerPlayer,
      vitals: VitalsComponentImpl
  ): Boolean = {
    val load = normalizeLoad(vitals.shock.load)
    applyLoad(player, vitals, load, load, PhysiologyChangeCause.AdrenalineEdit)
  }

  def resetHealthy(player: ServerPlayer, vitals: VitalsComponentImpl): Unit = {
    val previousLoad = normalizeLoad(vitals.shock.load)
    val previousStage = vitals.shock.stage
    VitalsMutations.applyShockState(vitals, ShockSnapshot(0.0, PainShockStage.Stable))
    if (previousStage != PainShockStage.Stable) {
      emitStageChange(
        player,
        previousStage,
        PainShockStage.Stable,
        previousLoad,
        0.0,
        PhysiologyChangeCause.Reset
      )
    }
  }

  /** Normalizes persisted/copy state without firing consciousness events. */
  private[casualtiesbelow] def normalizeStoredState(
      savedLoad: Double,
      savedStage: PainShockStage,
      savedUnconscious: Option[Boolean],
      adrenaline: Double
  ): ShockSnapshot = {
    val load = normalizeLoad(savedLoad)
    val threshold = collapseThreshold
    val effectiveThreshold = effectiveCollapseThreshold(
      threshold,
      adrenaline,
      Consts.Adrenaline.ShockProtectionPerPoint
    )
    // At exact equality retain the serialized phase: Collapsed means the load arrived from below,
    // while Recovering means it returned from above. The phase is the directional memory.
    val stage = savedStage match {
      case PainShockStage.Stable =>
        transition(
          PainShockStage.Stable,
          load,
          load,
          threshold,
          effectiveThreshold
        )
      case PainShockStage.Deferred =>
        transition(
          PainShockStage.Deferred,
          load,
          load,
          threshold,
          effectiveThreshold
        )
      case PainShockStage.Collapsed if load < threshold  => PainShockStage.Recovering
      case PainShockStage.Recovering if load > threshold => PainShockStage.Collapsed
      case PainShockStage.Recovering if savedUnconscious.contains(false) => PainShockStage.Stable
      case other                                                         => other
    }
    ShockSnapshot(load, stage)
  }

  /** Client-side component transport normalization. The server-authored stage is retained instead
    * of being re-derived from a potentially different client COMMON config.
    */
  private[casualtiesbelow] def normalizeSyncedState(
      savedLoad: Double,
      savedStage: PainShockStage
  ): ShockSnapshot = ShockSnapshot(normalizeLoad(savedLoad), savedStage)

  private def applyLoad(
      player: ServerPlayer,
      vitals: VitalsComponentImpl,
      previousLoad: Double,
      nextLoad: Double,
      cause: Identifier
  ): Boolean = {
    val previousStage = vitals.shock.stage
    val baseThreshold = collapseThreshold
    val effectiveThreshold = effectiveCollapseThreshold(
      baseThreshold,
      Adrenaline.currentAmount(vitals),
      Consts.Adrenaline.ShockProtectionPerPoint
    )
    val nextStage =
      transition(previousStage, previousLoad, nextLoad, baseThreshold, effectiveThreshold)
    if (nextLoad != vitals.shock.load || nextStage != previousStage) {
      VitalsMutations.applyShockState(vitals, ShockSnapshot(nextLoad, nextStage))
    }
    if (nextStage != previousStage) {
      emitStageChange(player, previousStage, nextStage, previousLoad, nextLoad, cause)
    }
    nextStage != previousStage
  }

  private def emitStageChange(
      player: ServerPlayer,
      previousStage: PainShockStage,
      stage: PainShockStage,
      previousLoad: Double,
      load: Double,
      cause: Identifier
  ): Unit = {
    PainShockStageChangedCallback.EVENT
      .invoker()
      .onPainShockStageChanged(
        new PainShockStageChangedContext(
          player,
          previousStage,
          stage,
          previousLoad,
          load,
          cause
        )
      )
  }

  private[casualtiesbelow] def transition(
      stage: PainShockStage,
      previousLoad: Double,
      nextLoad: Double,
      baseThreshold: Double,
      effectiveThreshold: Double
  ): PainShockStage = {
    stage match {
      case PainShockStage.Stable if nextLoad >= effectiveThreshold   => PainShockStage.Collapsed
      case PainShockStage.Stable if nextLoad >= baseThreshold        => PainShockStage.Deferred
      case PainShockStage.Deferred if nextLoad < baseThreshold       => PainShockStage.Stable
      case PainShockStage.Deferred if nextLoad >= effectiveThreshold =>
        PainShockStage.Collapsed
      case PainShockStage.Collapsed if nextLoad <= baseThreshold && nextLoad < previousLoad =>
        PainShockStage.Recovering
      case PainShockStage.Recovering if nextLoad >= baseThreshold && nextLoad > previousLoad =>
        PainShockStage.Collapsed
      case other => other
    }
  }

  /** Adrenaline protection deliberately may exceed [[MaxLoad]]: while load is capped at 100, the
    * reserve then controls how long the effective threshold takes to decay back down to it.
    */
  private[casualtiesbelow] def effectiveCollapseThreshold(
      baseThreshold: Double,
      adrenaline: Double,
      protectionPerPoint: Double
  ): Double = {
    val base = finite(baseThreshold).max(0.0).min(MaxLoad)
    val reserve = finite(adrenaline).max(0.0)
    val protection = finite(protectionPerPoint).max(0.0)
    base + reserve * protection
  }

  private def nextLoadFromPain(currentLoad: Double, totalPain: Double): Double = {
    val pain = finite(totalPain).max(0.0).min(100.0)
    val startPain = Consts.Pain.ShockAccumulationStartPain
    val fullRatePain =
      Consts.Pain.ShockMaximumRatePain.max(startPain)
    val maximumGain =
      Consts.Pain.ShockMaximumGainPerTick.max(0.0)
    val delta =
      if (pain <= startPain) {
        -Consts.Pain.ShockRecoveryPerTick.max(0.0)
      } else if (fullRatePain <= startPain) {
        maximumGain
      } else {
        maximumGain * ((pain - startPain) / (fullRatePain - startPain)).min(1.0)
      }
    (currentLoad + delta).max(0.0).min(MaxLoad)
  }

  private def collapseThreshold: Double = {
    Consts.Pain.ShockCollapseThreshold.max(0.0).min(MaxLoad)
  }

  private def crossedInteger(previousLoad: Double, nextLoad: Double): Boolean = {
    math.floor(previousLoad) != math.floor(nextLoad)
  }

  private def isWarningStage(stage: PainShockStage): Boolean = {
    stage == PainShockStage.Stable || stage == PainShockStage.Deferred
  }

  private def normalizeLoad(load: Double): Double = {
    if (load == Double.PositiveInfinity) MaxLoad
    else finite(load).max(0.0).min(MaxLoad)
  }

  private def finite(value: Double): Double = {
    if (value.isFinite) value else 0.0
  }
}
