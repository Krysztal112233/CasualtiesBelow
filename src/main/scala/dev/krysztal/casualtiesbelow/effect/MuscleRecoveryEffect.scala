package dev.krysztal.casualtiesbelow.effect

import dev.krysztal.casualtiesbelow.api.body.limb.LimbSnapshot
import dev.krysztal.casualtiesbelow.component.MutableLimbState
import dev.krysztal.casualtiesbelow.internal.Consts

/** Muscle Recovery: steadily repairs limb muscle health. Mirrors [[SkinRegenerationEffect]], minus
  * the bleeding-cap reconciliation — muscle regrowth has no coupled stat (see
  * Limb.tickMuscleRegen).
  */
private[casualtiesbelow] final class MuscleRecoveryEffect extends LimbRecoveryEffect(0xc0392b) {

  override protected def restorePerTick: Double = {
    Consts.Regeneration.MuscleRecoveryEffectPerTick
  }

  override protected def healthOf(stats: LimbSnapshot): Double = stats.muscleHealth

  override protected def heal(state: MutableLimbState, restore: Double): Unit = {
    if (state.muscleHealth < LimbSnapshot.MaxValue) {
      state.muscleHealth = (state.muscleHealth + restore).min(LimbSnapshot.MaxValue)
    }
  }
}
