package dev.krysztal.casualtiesbelow.physiology.progression

import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.component.ComponentAccess
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.physiology.blood.BloodVolume

/** Hidden terminal exposure after the blood-oxygen reserve is fully exhausted.
  *
  * Exposure accumulates only while oxygen is zero and respiration remains failed, whether from a
  * vanilla breathing block or severe opioid depression. Ending the failure resets it immediately,
  * even though blood oxygen and consciousness then recover gradually. The final fatal hit remains
  * owned by [[InjuryProgression]].
  */
object HypoxiaProgression {

  private val SyncIntervalTicks = 20

  /** Advances terminal exposure and reports both lethality and the sparse synchronization
    * milestones consumed by the client countdown. The timer is synchronized when it starts, once
    * per second, when it resets, and at its configured endpoint; it does not turn the whole vitals
    * component into a per-tick packet stream after oxygen has stabilized at zero.
    */
  def tick(vitals: VitalsComponentImpl, respirationFailed: Boolean): HypoxiaStep = {
    val step = advance(
      VitalsMutations.hypoxiaExposureTicks(vitals),
      vitals.circulation.bloodOxygen,
      respirationFailed,
      configuredDuration
    )
    if (step.changed) {
      VitalsMutations.applyHypoxiaExposureTicks(vitals, step.exposureTicks)
    }
    step
  }

  /** Completes the physiological side of a successful vanilla death-protection rescue. Oxygen is
    * restored only to current blood carrying capacity; healthy hypoxia can wake immediately, while
    * pain shock and inadequate blood oxygen retain their normal authority.
    */
  def onDeathProtection(player: ServerPlayer): Unit = {
    val vitals = ComponentAccess.vitals(player)
    reset(vitals)
    VitalsMutations.setBloodOxygen(vitals, BloodVolume.oxygenCarryingCapacity(vitals))
    ConsciousnessProgression.restoreAfterHypoxiaDeathProtection(player, vitals)
    VitalsMutations.syncNow(player)
  }

  def reset(vitals: VitalsComponentImpl): Boolean = {
    val changed = VitalsMutations.hypoxiaExposureTicks(vitals) != 0
    if (changed) {
      VitalsMutations.applyHypoxiaExposureTicks(vitals, 0)
    }
    changed
  }

  private[casualtiesbelow] def normalizeExposureTicks(ticks: Int): Int = {
    normalizeExposureTicks(ticks, configuredDuration)
  }

  private[casualtiesbelow] def advance(
      exposureTicks: Int,
      bloodOxygen: Double,
      respirationFailed: Boolean,
      durationTicks: Int
  ): HypoxiaStep = {
    val duration = durationTicks.max(1)
    val current = normalizeExposureTicks(exposureTicks, duration)
    val next =
      if (!respirationFailed) 0
      else if (bloodOxygen > 0.0) current
      else (current + 1).min(duration)
    val changed = next != current
    val syncDue =
      changed &&
        (next == 0 || next == 1 || next == duration || next % SyncIntervalTicks == 0)
    HypoxiaStep(
      exposureTicks = next,
      changed = changed,
      syncDue = syncDue,
      fatal = next >= duration
    )
  }

  private def configuredDuration: Int = {
    CasualtiesBelowConfig.TerminalHypoxiaDurationTicks.get().intValue.max(1)
  }

  private def normalizeExposureTicks(ticks: Int, duration: Int): Int = {
    ticks.max(0).min(duration.max(1))
  }
}

private[casualtiesbelow] final case class HypoxiaStep(
    exposureTicks: Int,
    changed: Boolean,
    syncDue: Boolean,
    fatal: Boolean
)
