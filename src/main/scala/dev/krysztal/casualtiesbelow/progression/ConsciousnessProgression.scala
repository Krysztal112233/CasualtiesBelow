package dev.krysztal.casualtiesbelow.progression

import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.body.VitalsComponent
import dev.krysztal.casualtiesbelow.api.event.ConsciousnessStateChangeCallback
import dev.krysztal.casualtiesbelow.api.event.ConsciousnessStateChangeContext
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.consciousness.Unconsciousness

/** Central authority for continuous consciousness and discrete unconsciousness transitions.
  *
  * Physiological sources contribute pressures rather than assigning consciousness directly. Harm
  * takes precedence over natural recovery; otherwise every active source must permit recovery.
  * Separate recovery and wake blockers leave room for future states such as pain shock, head
  * trauma, temperature stress, or sedation without letting healthy oxygen overwrite them.
  */
object ConsciousnessProgression {

  /** Advances consciousness and reconciles its hysteretic state. Returns whether either stored
    * value changed.
    */
  def tick(player: ServerPlayer, vitals: VitalsComponent): Boolean = {
    applyStep(
      player,
      vitals,
      advance(
        vitals.consciousness,
        vitals.unconscious,
        List(currentPressure(vitals)),
        CasualtiesBelowConfig.ConsciousnessRecoveryPerTick.get(),
        CasualtiesBelowConfig.ConsciousnessWakeThreshold.get(),
        CasualtiesBelowConfig.ConsciousnessFloor.get()
      )
    )
  }

  /** Applies an authoritative external consciousness edit, such as an admin command, and
    * immediately reconciles the hysteretic state. The scalar is not advanced or recovered here.
    */
  def applyAuthoritativeEdit(
      player: ServerPlayer,
      vitals: VitalsComponent,
      consciousness: Double
  ): Boolean = {
    applyStep(
      player,
      vitals,
      reconcile(
        consciousness,
        vitals.unconscious,
        List(currentPressure(vitals)),
        CasualtiesBelowConfig.ConsciousnessWakeThreshold.get(),
        CasualtiesBelowConfig.ConsciousnessFloor.get()
      )
    )
  }

  /** Reconciles the discrete state after another authoritative vitals edit changed the active
    * physiological pressure (for example blood oxygen).
    */
  def reconcileAfterEdit(player: ServerPlayer, vitals: VitalsComponent): Boolean = {
    applyStep(
      player,
      vitals,
      reconcile(
        vitals.consciousness,
        vitals.unconscious,
        List(currentPressure(vitals)),
        CasualtiesBelowConfig.ConsciousnessWakeThreshold.get(),
        CasualtiesBelowConfig.ConsciousnessFloor.get()
      )
    )
  }

  /** Explicit recovery reset. Unlike fresh death-respawn component construction, this is an
    * authoritative state change and therefore emits the same wake event as physiological recovery.
    */
  def resetHealthy(player: ServerPlayer, vitals: VitalsComponent): Boolean = {
    applyStep(player, vitals, ConsciousnessStep(VitalsComponent.MaxValue, false))
  }

  private def currentPressure(vitals: VitalsComponent): ConsciousnessPressure = {
    hypoxiaPressure(
      vitals.bloodOxygen,
      CasualtiesBelowConfig.BloodOxygenHypoxiaThreshold.get(),
      math.max(
        CasualtiesBelowConfig.BloodOxygenHypoxiaThreshold.get(),
        CasualtiesBelowConfig.ConsciousnessRecoveryOxygenThreshold.get()
      ),
      CasualtiesBelowConfig.HypoxiaConsciousnessDrainPerTick.get()
    )
  }

  private def applyStep(
      player: ServerPlayer,
      vitals: VitalsComponent,
      step: ConsciousnessStep
  ): Boolean = {
    val previousConsciousness = vitals.consciousness
    val previousUnconscious = vitals.unconscious
    vitals.applyConsciousnessState(step.consciousness, step.unconscious)

    if (step.unconscious != previousUnconscious) {
      if (step.unconscious) Unconsciousness.onEntered(player)
      ConsciousnessStateChangeCallback.EVENT
        .invoker()
        .onConsciousnessStateChange(
          ConsciousnessStateChangeContext(
            player,
            previousUnconscious,
            step.unconscious,
            step.consciousness
          )
        )
    }

    step.consciousness != previousConsciousness || step.unconscious != previousUnconscious
  }

  private[progression] def advance(
      current: Double,
      unconscious: Boolean,
      pressures: List[ConsciousnessPressure],
      recoveryPerTick: Double,
      wakeThreshold: Double,
      floor: Double
  ): ConsciousnessStep = {
    val ceiling = pressureCeiling(pressures).max(floor)
    val bounded = current.max(floor).min(ceiling)
    val totalLoss = pressures.map(_.lossPerTick.max(0.0)).sum
    val next =
      if (totalLoss > 0.0) {
        (bounded - totalLoss).max(floor)
      } else if (pressures.exists(_.recoveryBlocked)) {
        bounded
      } else {
        (bounded + recoveryPerTick.max(0.0)).min(ceiling)
      }
    reconcile(next, unconscious, pressures, wakeThreshold, floor)
  }

  private[progression] def reconcile(
      consciousness: Double,
      unconscious: Boolean,
      pressures: List[ConsciousnessPressure],
      wakeThreshold: Double,
      floor: Double
  ): ConsciousnessStep = {
    val bounded = consciousness.max(floor).min(pressureCeiling(pressures).max(floor))
    val nextUnconscious =
      if (!unconscious && bounded <= floor) {
        true
      } else if (
        unconscious &&
        bounded >= wakeThreshold
          .max(VitalsComponent.MinimumWakeThreshold)
          .min(VitalsComponent.MaxValue) &&
        !pressures.exists(_.wakeBlocked)
      ) {
        false
      } else {
        unconscious
      }
    ConsciousnessStep(bounded, nextUnconscious)
  }

  private def pressureCeiling(pressures: List[ConsciousnessPressure]): Double = {
    pressures
      .map(_.ceiling)
      .minOption
      .getOrElse(VitalsComponent.MaxValue)
      .max(0.0)
      .min(VitalsComponent.MaxValue)
  }

  private[progression] def hypoxiaPressure(
      bloodOxygen: Double,
      hypoxiaThreshold: Double,
      recoveryThreshold: Double,
      maximumDrainPerTick: Double
  ): ConsciousnessPressure = {
    val oxygen = bloodOxygen.max(0.0).min(VitalsComponent.MaxBloodOxygen)
    val hypoxia = hypoxiaThreshold.max(0.0).min(VitalsComponent.MaxBloodOxygen)
    val recovery = recoveryThreshold.max(hypoxia).min(VitalsComponent.MaxBloodOxygen)

    if (hypoxia > 0.0 && oxygen < hypoxia) {
      ConsciousnessPressure(
        lossPerTick = maximumDrainPerTick.max(0.0) * (1.0 - oxygen / hypoxia),
        recoveryBlocked = true,
        wakeBlocked = true
      )
    } else if (oxygen < recovery) {
      ConsciousnessPressure(recoveryBlocked = true, wakeBlocked = true)
    } else {
      ConsciousnessPressure()
    }
  }

  /** Normalizes serialized or copied state without firing a transition event. A scalar at or below
    * the floor always implies unconsciousness; a latched scalar above the floor remains valid
    * inside the wake hysteresis band. NaN falls back conservatively according to the saved latch,
    * while infinities clamp to the corresponding endpoint.
    */
  private[casualtiesbelow] def normalizeStoredState(
      savedConsciousness: Double,
      savedUnconscious: Option[Boolean],
      floor: Double
  ): (Double, Boolean) = {
    val raw =
      if (java.lang.Double.isFinite(savedConsciousness)) {
        savedConsciousness
      } else if (savedConsciousness == Double.PositiveInfinity) {
        VitalsComponent.MaxValue
      } else if (savedConsciousness == Double.NegativeInfinity) {
        floor
      } else if (savedUnconscious.contains(true)) {
        floor
      } else {
        VitalsComponent.MaxValue
      }
    val consciousness = raw.max(floor).min(VitalsComponent.MaxValue)
    val unconscious = savedUnconscious.getOrElse(raw <= floor) || raw <= floor
    (consciousness, unconscious)
  }
}

private[progression] final case class ConsciousnessPressure(
    lossPerTick: Double = 0.0,
    recoveryBlocked: Boolean = false,
    wakeBlocked: Boolean = false,
    ceiling: Double = VitalsComponent.MaxValue
)

private[progression] final case class ConsciousnessStep(
    consciousness: Double,
    unconscious: Boolean
)
