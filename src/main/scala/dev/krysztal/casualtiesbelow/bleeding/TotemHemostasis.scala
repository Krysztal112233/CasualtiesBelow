package dev.krysztal.casualtiesbelow.bleeding

import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.VitalsComponent
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

/** Physiological adapter applied only after vanilla death protection rescues blood loss.
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
    val vitals = CasualtiesBelowComponents.Vitals.get(player)
    val effectiveMaxBlood =
      CasualtiesBelowConfig.effectiveMaxBloodVolume(vitals.sepsis).doubleValue.max(0.0)
    val restoreFraction =
      CasualtiesBelowConfig.TotemBloodRestoreFraction
        .get()
        .doubleValue
        .max(MinimumRestoreFraction)
        .min(1.0)
    val restoredBlood = effectiveMaxBlood * restoreFraction
    vitals.bloodVolume = (vitals.bloodVolume.max(0.0) + restoredBlood).min(effectiveMaxBlood)
    vitals.applyTotemHemostasisTicks(configuredDurationTicks)
    CasualtiesBelowComponents.Vitals.sync(player)
  }

  /** Multiplier applied to this tick's summed external bleeding. At activation it is `1 - initial
    * reduction`, then approaches one linearly as the timer expires.
    */
  def bleedingMultiplier(vitals: VitalsComponent): Double = {
    val duration = configuredDurationTicks
    val remaining = normalizeRemainingTicks(vitals.totemHemostasisTicks)
    if (duration <= 0 || remaining <= 0) return 1.0

    val initialReduction =
      CasualtiesBelowConfig.TotemHemostasisInitialReduction.get().doubleValue.max(0.0).min(1.0)
    1.0 - initialReduction * remaining.toDouble / duration.toDouble
  }

  /** Advances the hidden countdown without forcing a client sync. */
  def tick(vitals: VitalsComponent): Unit = {
    val remaining = normalizeRemainingTicks(vitals.totemHemostasisTicks)
    vitals.applyTotemHemostasisTicks((remaining - 1).max(0))
  }

  def reset(vitals: VitalsComponent): Unit = {
    vitals.applyTotemHemostasisTicks(0)
  }

  private[casualtiesbelow] def normalizeRemainingTicks(ticks: Int): Int = {
    ticks.max(0).min(configuredDurationTicks)
  }

  private def configuredDurationTicks: Int = {
    CasualtiesBelowConfig.TotemHemostasisDurationTicks.get().intValue.max(0)
  }

  private val MinimumRestoreFraction = 0.000001
}
