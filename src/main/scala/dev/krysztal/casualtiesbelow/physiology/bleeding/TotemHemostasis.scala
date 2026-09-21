package dev.krysztal.casualtiesbelow.physiology.bleeding

import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.extension.ConfigValueExtensions.*
import dev.krysztal.casualtiesbelow.internal.extension.PlayerExtensions.*
import dev.krysztal.casualtiesbelow.physiology.blood.BloodVolume

/** Physiological blood adapter after vanilla death protection rescues blood loss or starvation.
  *
  * It restores a bounded blood reserve and temporarily reduces the actual whole-body blood drain.
  * Limb bleeding rates remain untouched, so clotting and treatment continue to operate on the real
  * wounds. The hidden countdown is server-authoritative and freezes whenever injury progression is
  * frozen (creative or spectator mode).
  */
object TotemHemostasis {

  /** Applies the user-configured rescue and immediately syncs the restored blood volume. Vanilla
    * has already consumed the protection item and applied its normal effects before this runs.
    */
  def activate(player: ServerPlayer): Unit = {
    val vitals = player.vitals
    val effectiveMaxBlood = BloodVolume.effectiveMaximum(vitals)
    val restoreFraction =
      CasualtiesBelowConfig.TotemBloodRestoreFraction
        .get()
        .doubleValue
        .max(MinimumRestoreFraction)
        .min(1.0)
    val restoredBlood = effectiveMaxBlood * restoreFraction
    BloodVolume.restore(vitals, restoredBlood, effectiveMaxBlood)
    VitalsMutations.applyTotemHemostasisTicks(vitals, configuredDurationTicks)
    VitalsMutations.syncNow(player)
  }

  /** Multiplier applied to this tick's summed external bleeding. At activation it is `1 - initial
    * reduction`, then approaches one linearly as the timer expires.
    */
  def bleedingMultiplier(vitals: VitalsComponentImpl): Double = {
    val duration = configuredDurationTicks
    val remaining = normalizeRemainingTicks(VitalsMutations.totemHemostasisTicks(vitals))
    if (duration <= 0 || remaining <= 0) return 1.0

    val initialReduction =
      CasualtiesBelowConfig.TotemHemostasisInitialReduction.value.doubleValue.max(0.0).min(1.0)
    1.0 - initialReduction * remaining.toDouble / duration.toDouble
  }

  /** Advances the hidden countdown without forcing a client sync. */
  def tick(vitals: VitalsComponentImpl): Unit = {
    val remaining = normalizeRemainingTicks(VitalsMutations.totemHemostasisTicks(vitals))
    VitalsMutations.applyTotemHemostasisTicks(vitals, (remaining - 1).max(0))
  }

  def reset(vitals: VitalsComponentImpl): Unit = {
    VitalsMutations.applyTotemHemostasisTicks(vitals, 0)
  }

  private[casualtiesbelow] def normalizeRemainingTicks(ticks: Int): Int = {
    ticks.max(0).min(configuredDurationTicks)
  }

  private def configuredDurationTicks: Int = {
    CasualtiesBelowConfig.TotemHemostasisDurationTicks.value.intValue.max(0)
  }

  private val MinimumRestoreFraction = 0.000001
}
