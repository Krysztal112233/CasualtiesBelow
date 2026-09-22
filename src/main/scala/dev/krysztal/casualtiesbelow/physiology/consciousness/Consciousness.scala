package dev.krysztal.casualtiesbelow.physiology.consciousness

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.body.vitals.ConsciousnessSnapshot
import dev.krysztal.casualtiesbelow.api.body.vitals.PainShockStage
import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.api.event.ConsciousnessStateChangeCallback
import dev.krysztal.casualtiesbelow.api.event.ConsciousnessStateChangeContext
import dev.krysztal.casualtiesbelow.api.event.PhysiologyChangeCause
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.physiology.consciousness.Unconsciousness
import dev.krysztal.casualtiesbelow.physiology.opioid.OpioidEffects
import dev.krysztal.casualtiesbelow.physiology.temperature.TemperatureCalc

/** Central authority for continuous consciousness and discrete unconsciousness transitions.
  *
  * Physiological sources contribute pressures rather than assigning consciousness directly. Harm
  * takes precedence over natural recovery; otherwise every active source must permit recovery.
  * Separate recovery and wake blockers leave room for future states such as head trauma or sedation
  * without letting healthy oxygen overwrite them. Pressure sources: low blood oxygen (recovery/wake
  * blocker), opioid sedation, and temperature stress outside the penalty band (a ceiling, like
  * oxygen). Pain shock is a stronger discrete override: its collapsed phase owns literal-zero
  * consciousness, then its recovery phase temporarily lowers the scalar bound to zero.
  */
private[casualtiesbelow] object Consciousness {

  /** Advances consciousness and reconciles its hysteretic state. Returns whether either stored
    * value changed.
    */
  def tick(player: ServerPlayer, vitals: VitalsComponentImpl): Boolean = {
    val step = vitals.shock.stage match {
      case PainShockStage.Collapsed => ConsciousnessSnapshot(0.0, true)
      case _                        =>
        advance(
          vitals.consciousness.level,
          vitals.consciousness.unconscious,
          currentPressures(vitals),
          CasualtiesBelowConfig.ConsciousnessRecoveryPerTick.get(),
          configuredWakeThreshold,
          effectiveKnockoutThreshold(vitals),
          effectiveFloor(vitals)
        )
    }
    applyStep(player, vitals, step, PhysiologyChangeCause.Progression)
  }

  /** Applies an authoritative external consciousness edit, such as an admin command, and
    * immediately reconciles the hysteretic state. The scalar is not advanced or recovered here.
    */
  def applyAuthoritativeEdit(
      player: ServerPlayer,
      vitals: VitalsComponentImpl,
      consciousness: Double
  ): Boolean = {
    val step = vitals.shock.stage match {
      case PainShockStage.Collapsed => ConsciousnessSnapshot(0.0, true)
      case _                        =>
        reconcile(
          consciousness,
          vitals.consciousness.unconscious,
          currentPressures(vitals),
          configuredWakeThreshold,
          effectiveKnockoutThreshold(vitals),
          effectiveFloor(vitals)
        )
    }
    applyStep(player, vitals, step, PhysiologyChangeCause.AdminEdit)
  }

  /** Reconciles the discrete state after another authoritative vitals edit changed the active
    * physiological pressure (for example blood oxygen).
    */
  def reconcileAfterEdit(player: ServerPlayer, vitals: VitalsComponentImpl): Boolean = {
    val step = vitals.shock.stage match {
      case PainShockStage.Collapsed => ConsciousnessSnapshot(0.0, true)
      case _                        =>
        reconcile(
          vitals.consciousness.level,
          vitals.consciousness.unconscious,
          currentPressures(vitals),
          configuredWakeThreshold,
          effectiveKnockoutThreshold(vitals),
          effectiveFloor(vitals)
        )
    }
    applyStep(player, vitals, step, PhysiologyChangeCause.AdminEdit)
  }

  /** Explicit recovery reset. Unlike fresh death-respawn component construction, this is an
    * authoritative state change and therefore emits the same wake event as physiological recovery.
    */
  def resetHealthy(player: ServerPlayer, vitals: VitalsComponentImpl): Boolean = {
    applyStep(
      player,
      vitals,
      ConsciousnessSnapshot(VitalsComponent.MaxValue, false),
      PhysiologyChangeCause.Reset
    )
  }

  /** Reconciles a successful hypoxia death-protection rescue at no less than the configured wake
    * threshold. A collapsed pain-shock episode remains literal-zero and unconscious; low oxygen
    * capacity can likewise keep waking blocked.
    */
  private[casualtiesbelow] def restoreAfterHypoxiaDeathProtection(
      player: ServerPlayer,
      vitals: VitalsComponentImpl
  ): Boolean = {
    val wakeThreshold = configuredWakeThreshold
    val step = vitals.shock.stage match {
      case PainShockStage.Collapsed => ConsciousnessSnapshot(0.0, true)
      case _                        =>
        reconcile(
          vitals.consciousness.level.max(wakeThreshold),
          vitals.consciousness.unconscious,
          currentPressures(vitals),
          wakeThreshold,
          effectiveKnockoutThreshold(vitals),
          effectiveFloor(vitals)
        )
    }
    applyStep(player, vitals, step, PhysiologyChangeCause.HypoxiaDeathProtection)
  }

  private def configuredWakeThreshold: Double = {
    CasualtiesBelowConfig.effectiveConsciousnessWakeThreshold
  }

  private def configuredKnockoutThreshold: Double = {
    CasualtiesBelowConfig.effectiveConsciousnessKnockoutThreshold
  }

  private def effectiveFloor(vitals: VitalsComponentImpl): Double = {
    vitals.shock.stage match {
      case PainShockStage.Recovering => 0.0
      case _                         => CasualtiesBelowConfig.effectiveConsciousnessFloor
    }
  }

  private def effectiveKnockoutThreshold(vitals: VitalsComponentImpl): Double = {
    vitals.shock.stage match {
      case PainShockStage.Recovering => 0.0
      case _                         => configuredKnockoutThreshold
    }
  }

  private def currentPressures(vitals: VitalsComponentImpl): List[ConsciousnessPressure] = {
    List(
      hypoxiaPressure(
        vitals.circulation.bloodOxygen,
        CasualtiesBelowConfig.ConsciousnessRecoveryOxygenThreshold.get(),
        CasualtiesBelowConfig.ConsciousnessOxygenCapMultiplier.get()
      ),
      ConsciousnessPressure(ceiling = OpioidEffects.consciousnessCeiling(vitals.opioidLevel)),
      temperaturePressure(vitals.bodyTemperature)
    )
  }

  /** Temperature stress: deviation outside the penalty band caps consciousness like low blood
    * oxygen does (heatstroke and hypothermia present as impaired awareness). The cap is not a
    * knockout while it stays above the knockout threshold; below it the capped consciousness itself
    * produces coma — it cannot reach the wake threshold — until the body rewarms, which is the
    * deep-band behavior the terminal tier will own.
    */
  private[casualtiesbelow] def temperaturePressure(
      bodyTemperature: Double
  ): ConsciousnessPressure = {
    val coldDev = TemperatureCalc.coldDeviation(
      bodyTemperature,
      CasualtiesBelowConfig.PenaltyBandLowCelsius.get()
    )
    val hotDev = TemperatureCalc.hotDeviation(
      bodyTemperature,
      CasualtiesBelowConfig.PenaltyBandHighCelsius.get()
    )
    val ceiling = finiteInRange(
      CasualtiesBelowConfig.TemperatureConsciousnessCeilingFormula.evaluate(
        coldDev,
        hotDev,
        CasualtiesBelowConfig.ColdConsciousnessSlopePerDegree.get(),
        CasualtiesBelowConfig.HotConsciousnessSlopePerDegree.get()
      ),
      0.0,
      VitalsComponent.MaxValue,
      VitalsComponent.MaxValue
    )
    ConsciousnessPressure(ceiling = ceiling)
  }

  private def applyStep(
      player: ServerPlayer,
      vitals: VitalsComponentImpl,
      step: ConsciousnessSnapshot,
      cause: Identifier
  ): Boolean = {
    val previousConsciousness = vitals.consciousness.level
    val previousUnconscious = vitals.consciousness.unconscious
    VitalsMutations.applyConsciousnessState(vitals, step)

    if (step.unconscious != previousUnconscious) {
      if (step.unconscious) Unconsciousness.onEntered(player)
      ConsciousnessStateChangeCallback.EVENT
        .invoker()
        .onConsciousnessStateChange(
          new ConsciousnessStateChangeContext(
            player,
            previousConsciousness,
            step.level,
            previousUnconscious,
            step.unconscious,
            vitals.shock.stage,
            cause
          )
        )
    }

    step.level != previousConsciousness || step.unconscious != previousUnconscious
  }

  private[casualtiesbelow] def advance(
      current: Double,
      unconscious: Boolean,
      pressures: List[ConsciousnessPressure],
      recoveryPerTick: Double,
      wakeThreshold: Double,
      knockoutThreshold: Double,
      floor: Double
  ): ConsciousnessSnapshot = {
    val minimum = finiteInRange(floor, 0.0, VitalsComponent.MaxValue, 0.0)
    val ceiling = pressureCeiling(pressures).max(minimum)
    val bounded = normalizedConsciousness(current, minimum, ceiling)
    val currentAllowsRecovery = current.isFinite || current == Double.PositiveInfinity
    val totalLoss = pressures.map(pressure => finiteNonNegative(pressure.lossPerTick)).sum
    val next =
      if (totalLoss > 0.0) {
        (bounded - totalLoss).max(minimum)
      } else if (pressures.exists(_.recoveryBlocked) || !currentAllowsRecovery) {
        bounded
      } else {
        (bounded + finiteNonNegative(recoveryPerTick)).min(ceiling)
      }
    reconcile(next, unconscious, pressures, wakeThreshold, knockoutThreshold, minimum)
  }

  private[casualtiesbelow] def reconcile(
      consciousness: Double,
      unconscious: Boolean,
      pressures: List[ConsciousnessPressure],
      wakeThreshold: Double,
      knockoutThreshold: Double,
      floor: Double
  ): ConsciousnessSnapshot = {
    val minimum = finiteInRange(floor, 0.0, VitalsComponent.MaxValue, 0.0)
    val ceiling = pressureCeiling(pressures).max(minimum)
    val bounded = normalizedConsciousness(consciousness, minimum, ceiling)
    val knockout = finiteInRange(
      knockoutThreshold,
      minimum,
      VitalsComponent.MaxValue,
      minimum
    )
    val minimumWake = minimumWakeThreshold(knockout)
    val wake = finiteInRange(
      wakeThreshold,
      minimumWake,
      VitalsComponent.MaxValue,
      minimumWake
    )
    val nextUnconscious =
      if (!unconscious && bounded <= knockout) {
        true
      } else if (
        unconscious &&
        wake > knockout &&
        bounded >= wake &&
        !pressures.exists(_.wakeBlocked)
      ) {
        false
      } else {
        unconscious
      }
    ConsciousnessSnapshot(bounded, nextUnconscious)
  }

  private def minimumWakeThreshold(knockoutThreshold: Double): Double = {
    (knockoutThreshold + VitalsComponent.MinimumWakeThreshold).min(VitalsComponent.MaxValue)
  }

  private def pressureCeiling(pressures: List[ConsciousnessPressure]): Double = {
    pressures
      .map(pressure => finiteInRange(pressure.ceiling, 0.0, VitalsComponent.MaxValue, 0.0))
      .minOption
      .getOrElse(VitalsComponent.MaxValue)
      .max(0.0)
      .min(VitalsComponent.MaxValue)
  }

  private[casualtiesbelow] def hypoxiaPressure(
      bloodOxygen: Double,
      recoveryThreshold: Double,
      oxygenCapMultiplier: Double
  ): ConsciousnessPressure = {
    val oxygen = finiteInRange(
      bloodOxygen,
      0.0,
      VitalsComponent.MaxBloodOxygen,
      0.0
    )
    val recovery = finiteInRange(
      recoveryThreshold,
      0.0,
      VitalsComponent.MaxBloodOxygen,
      VitalsComponent.MaxBloodOxygen
    )
    val multiplier = finiteNonNegative(oxygenCapMultiplier)
    val ceiling = finiteInRange(
      oxygen * multiplier,
      0.0,
      VitalsComponent.MaxValue,
      0.0
    )
    ConsciousnessPressure(
      recoveryBlocked = oxygen < recovery,
      wakeBlocked = oxygen < recovery,
      ceiling = ceiling
    )
  }

  private def finiteNonNegative(value: Double): Double = {
    if (value == Double.PositiveInfinity) Double.MaxValue
    else if (value.isFinite) value.max(0.0)
    else 0.0
  }

  private def finiteInRange(
      value: Double,
      minimum: Double,
      maximum: Double,
      fallback: Double
  ): Double = {
    if (value == Double.PositiveInfinity) maximum
    else if (value == Double.NegativeInfinity) minimum
    else if (value.isFinite) value.max(minimum).min(maximum)
    else fallback.max(minimum).min(maximum)
  }

  private def normalizedConsciousness(
      value: Double,
      minimum: Double,
      maximum: Double
  ): Double = {
    if (value == Double.PositiveInfinity) maximum
    else if (value.isFinite) value.max(minimum).min(maximum)
    else minimum
  }

  /** Normalizes serialized or copied state without firing a transition event. Stable physiology
    * keeps the configured floor invariant. A collapsed pain-shock episode is always literal zero
    * and unconscious; its recovery phase preserves a scalar in [0, 100] and the unconscious latch
    * so recovery can resume below the ordinary floor. NaN falls back conservatively according to
    * the phase/latch, while infinities clamp to the corresponding endpoint.
    */
  private[casualtiesbelow] def normalizeStoredState(
      savedConsciousness: Double,
      savedUnconscious: Option[Boolean],
      floor: Double,
      knockoutThreshold: Double,
      painShockStage: PainShockStage
  ): ConsciousnessSnapshot = {
    if (painShockStage == PainShockStage.Collapsed) {
      return ConsciousnessSnapshot(0.0, true)
    }

    val minimum =
      if (painShockStage == PainShockStage.Recovering) 0.0
      else floor
    val raw =
      if (savedConsciousness.isFinite) {
        savedConsciousness
      } else if (savedConsciousness == Double.PositiveInfinity) {
        VitalsComponent.MaxValue
      } else if (savedConsciousness == Double.NegativeInfinity) {
        minimum
      } else if (painShockStage == PainShockStage.Recovering || savedUnconscious.contains(true)) {
        minimum
      } else {
        VitalsComponent.MaxValue
      }
    val consciousness = raw.max(minimum).min(VitalsComponent.MaxValue)
    val knockout = knockoutThreshold.max(minimum).min(VitalsComponent.MaxValue)
    val unconscious =
      if (painShockStage == PainShockStage.Recovering) true
      else savedUnconscious.getOrElse(consciousness <= knockout) || consciousness <= knockout
    ConsciousnessSnapshot(consciousness, unconscious)
  }

  /** Normalizes a server-synchronized client copy without re-deriving the authoritative latch from
    * local config. Pain-shock stages retain their intrinsic collapsed/recovering invariants.
    */
  private[casualtiesbelow] def normalizeSyncedState(
      savedConsciousness: Double,
      savedUnconscious: Option[Boolean],
      painShockStage: PainShockStage
  ): ConsciousnessSnapshot = {
    if (painShockStage == PainShockStage.Collapsed) return ConsciousnessSnapshot(0.0, true)

    val raw =
      if (savedConsciousness.isFinite) savedConsciousness
      else if (savedConsciousness == Double.PositiveInfinity) VitalsComponent.MaxValue
      else if (savedConsciousness == Double.NegativeInfinity) 0.0
      else if (painShockStage == PainShockStage.Recovering || savedUnconscious.contains(true)) 0.0
      else VitalsComponent.MaxValue
    val consciousness = raw.max(0.0).min(VitalsComponent.MaxValue)
    val unconscious =
      if (painShockStage == PainShockStage.Recovering) true
      else savedUnconscious.getOrElse(false)
    ConsciousnessSnapshot(consciousness, unconscious)
  }
}

private[casualtiesbelow] final case class ConsciousnessPressure(
    lossPerTick: Double = 0.0,
    recoveryBlocked: Boolean = false,
    wakeBlocked: Boolean = false,
    ceiling: Double = VitalsComponent.MaxValue
)
