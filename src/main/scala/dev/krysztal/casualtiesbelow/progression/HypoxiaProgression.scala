package dev.krysztal.casualtiesbelow.progression

import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.VitalsComponent
import dev.krysztal.casualtiesbelow.blood.BloodVolume
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

/** Hidden terminal exposure after the blood-oxygen reserve is fully exhausted.
  *
  * Exposure accumulates only while oxygen is zero and breathing remains blocked. Ending the block
  * resets it immediately, even though blood oxygen and consciousness then recover gradually. The
  * final fatal hit remains owned by [[InjuryProgression]].
  */
object HypoxiaProgression {

  /** Advances terminal exposure and returns whether its positive configured duration has elapsed.
    */
  def tick(vitals: VitalsComponent, breathingBlocked: Boolean): Boolean = {
    if (!breathingBlocked) {
      reset(vitals)
      return false
    }
    if (vitals.bloodOxygen > 0.0) return false

    val next = (normalizeExposureTicks(vitals.hypoxiaExposureTicks) + 1).min(configuredDuration)
    vitals.applyHypoxiaExposureTicks(next)
    next >= configuredDuration
  }

  /** Completes the physiological side of a successful vanilla death-protection rescue. Oxygen is
    * restored only to current blood carrying capacity; healthy hypoxia can wake immediately, while
    * pain shock and inadequate blood oxygen retain their normal authority.
    */
  def onDeathProtection(player: ServerPlayer): Unit = {
    val vitals = CasualtiesBelowComponents.Vitals.get(player)
    reset(vitals)
    vitals.bloodOxygen = BloodVolume.oxygenCarryingCapacity(vitals)
    ConsciousnessProgression.restoreAfterHypoxiaDeathProtection(player, vitals)
    CasualtiesBelowComponents.Vitals.sync(player)
  }

  def reset(vitals: VitalsComponent): Unit = {
    vitals.applyHypoxiaExposureTicks(0)
  }

  private[casualtiesbelow] def normalizeExposureTicks(ticks: Int): Int = {
    ticks.max(0).min(configuredDuration)
  }

  private def configuredDuration: Int = {
    CasualtiesBelowConfig.TerminalHypoxiaDurationTicks.get().intValue.max(1)
  }
}
