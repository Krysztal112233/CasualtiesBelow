package dev.krysztal.casualtiesbelow.pain

import dev.krysztal.casualtiesbelow.api.body.BodyComponent
import dev.krysztal.casualtiesbelow.api.body.PainShockStage
import dev.krysztal.casualtiesbelow.api.body.VitalsComponent
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

/** Hidden pain-shock load and its discrete collapse/recovery lifecycle.
  *
  * Whole-body pain is the only source. Pain at or below the configured start threshold lets load
  * recover; pain above it accumulates load linearly until the configured full-rate threshold, then
  * remains at the maximum rate. Crossing the collapse threshold upward starts an episode, while
  * crossing it downward permits consciousness recovery. Direction is encoded by [[PainShockStage]]
  * rather than by separate entry and exit numbers.
  *
  * Load remains hidden from numeric UI, but crossing an integer during [[PainShockStage.Stable]]
  * requests an owner-only component sync for smooth, server-authoritative peripheral warning
  * visuals. Stage transitions still sync immediately because they alter consciousness behavior.
  */
object PainShock {
  val MaxLoad: Double = 100.0

  /** Advances load from current whole-body pain and reconciles the shock phase. Returns whether the
    * phase changed or stable load crossed an integer boundary and therefore requires a vitals sync.
    */
  def tick(body: BodyComponent, vitals: VitalsComponent): Boolean = {
    val previousLoad = normalizeLoad(vitals.painShockLoad)
    val previousStage = vitals.painShockStage
    val nextLoad = nextLoadFromPain(previousLoad, PainCalc.total(body))
    applyLoad(vitals, previousLoad, nextLoad) ||
    (previousStage == PainShockStage.Stable && crossedInteger(previousLoad, nextLoad))
  }

  /** Clears the recovery phase after the centralized consciousness authority actually wakes the
    * player, clamping residual load to the configured post-wake cap. Returns whether the phase
    * changed.
    */
  def finishRecovery(vitals: VitalsComponent): Boolean = {
    if (vitals.painShockStage != PainShockStage.Recovering || vitals.unconscious) {
      return false
    }

    val wakeLoadCap =
      CasualtiesBelowConfig.ShockWakeLoadCap.get().doubleValue.max(0.0).min(MaxLoad)
    val retainedLoad = normalizeLoad(vitals.painShockLoad).min(wakeLoadCap)
    vitals.applyPainShockState(retainedLoad, PainShockStage.Stable)
    true
  }

  /** Authoritative debug edit. The old and new load values preserve the same directional threshold
    * semantics as physiological progression.
    */
  def applyAuthoritativeEdit(vitals: VitalsComponent, requestedLoad: Double): Boolean = {
    val previousLoad = normalizeLoad(vitals.painShockLoad)
    applyLoad(vitals, previousLoad, normalizeLoad(requestedLoad))
  }

  def resetHealthy(vitals: VitalsComponent): Unit = {
    vitals.applyPainShockState(0.0, PainShockStage.Stable)
  }

  /** Normalizes persisted/copy state without firing consciousness events. */
  private[casualtiesbelow] def normalizeStoredState(
      savedLoad: Double,
      savedStage: PainShockStage,
      savedUnconscious: Option[Boolean]
  ): (Double, PainShockStage) = {
    val load = normalizeLoad(savedLoad)
    val threshold = collapseThreshold
    // At exact equality retain the serialized phase: Collapsed means the load arrived from below,
    // while Recovering means it returned from above. The phase is the directional memory.
    val stage = savedStage match {
      case PainShockStage.Stable if load >= threshold    => PainShockStage.Collapsed
      case PainShockStage.Collapsed if load < threshold  => PainShockStage.Recovering
      case PainShockStage.Recovering if load > threshold => PainShockStage.Collapsed
      case PainShockStage.Recovering if savedUnconscious.contains(false) => PainShockStage.Stable
      case other                                                         => other
    }
    (load, stage)
  }

  private def applyLoad(
      vitals: VitalsComponent,
      previousLoad: Double,
      nextLoad: Double
  ): Boolean = {
    val previousStage = vitals.painShockStage
    val nextStage = transition(previousStage, previousLoad, nextLoad)
    if (nextLoad != vitals.painShockLoad || nextStage != previousStage) {
      vitals.applyPainShockState(nextLoad, nextStage)
    }
    nextStage != previousStage
  }

  private def transition(
      stage: PainShockStage,
      previousLoad: Double,
      nextLoad: Double
  ): PainShockStage = {
    val threshold = collapseThreshold
    stage match {
      case PainShockStage.Stable if nextLoad >= threshold => PainShockStage.Collapsed
      case PainShockStage.Collapsed if nextLoad <= threshold && nextLoad < previousLoad =>
        PainShockStage.Recovering
      case PainShockStage.Recovering if nextLoad >= threshold && nextLoad > previousLoad =>
        PainShockStage.Collapsed
      case other => other
    }
  }

  private def nextLoadFromPain(currentLoad: Double, totalPain: Double): Double = {
    val pain = finite(totalPain).max(0.0).min(100.0)
    val startPain = CasualtiesBelowConfig.ShockAccumulationStartPain.get().doubleValue
    val fullRatePain =
      CasualtiesBelowConfig.ShockMaximumRatePain.get().doubleValue.max(startPain)
    val maximumGain =
      CasualtiesBelowConfig.ShockMaximumGainPerTick.get().doubleValue.max(0.0)
    val delta =
      if (pain <= startPain) {
        -CasualtiesBelowConfig.ShockRecoveryPerTick.get().doubleValue.max(0.0)
      } else if (fullRatePain <= startPain) {
        maximumGain
      } else {
        maximumGain * ((pain - startPain) / (fullRatePain - startPain)).min(1.0)
      }
    (currentLoad + delta).max(0.0).min(MaxLoad)
  }

  private def collapseThreshold: Double = {
    CasualtiesBelowConfig.ShockCollapseThreshold.get().doubleValue.max(0.0).min(MaxLoad)
  }

  private def crossedInteger(previousLoad: Double, nextLoad: Double): Boolean = {
    math.floor(previousLoad) != math.floor(nextLoad)
  }

  private def normalizeLoad(load: Double): Double = {
    if (load == Double.PositiveInfinity) MaxLoad
    else finite(load).max(0.0).min(MaxLoad)
  }

  private def finite(value: Double): Double = {
    if (value.isFinite) value else 0.0
  }
}
