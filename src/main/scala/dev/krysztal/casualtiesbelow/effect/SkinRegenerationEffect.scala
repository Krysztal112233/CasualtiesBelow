package dev.krysztal.casualtiesbelow.effect

import dev.krysztal.casualtiesbelow.api.body.limb.LimbSnapshot
import dev.krysztal.casualtiesbelow.component.MutableLimbState
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.physiology.limb.BleedingCalc

/** Skin Regeneration: steadily repairs limb skin integrity, even while a wound is still bleeding.
  * Raising skin immediately lowers that limb's allowed bleeding cap, mirroring the
  * vanilla-Regeneration micro-repair in Limb.
  */
private[casualtiesbelow] final class SkinRegenerationEffect extends LimbRecoveryEffect(0xe8a08c) {

  override protected def restorePerTick: Double = {
    Consts.Regeneration.SkinRegenerationEffectPerTick
  }

  override protected def healthOf(stats: LimbSnapshot): Double = stats.skinIntegrity

  override protected def heal(state: MutableLimbState, restore: Double): Unit = {
    if (state.skinIntegrity < LimbSnapshot.MaxValue) {
      state.skinIntegrity = (state.skinIntegrity + restore).min(LimbSnapshot.MaxValue)
      state.externalBleedingRate =
        state.externalBleedingRate.min(BleedingCalc.cap(state.skinIntegrity))
    }
  }

  override protected def isDiscreteTransition(
      before: LimbSnapshot,
      after: LimbSnapshot
  ): Boolean = {
    val stoppedBleeding = before.externalBleedingRate > 0.0 && after.externalBleedingRate <= 0.0
    super.isDiscreteTransition(before, after) || stoppedBleeding
  }
}
