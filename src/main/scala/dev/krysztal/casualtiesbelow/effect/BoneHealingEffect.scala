package dev.krysztal.casualtiesbelow.effect

import dev.krysztal.casualtiesbelow.api.body.limb.LimbSnapshot
import dev.krysztal.casualtiesbelow.component.MutableLimbState
import dev.krysztal.casualtiesbelow.internal.Consts

/** Bone Healing: steadily advances limb fracture recovery countdowns. Mirrors Limb.tickFracture's
  * completion rule (a countdown at 1 tick or less heals), so a limb mended by this effect ends in
  * the same healed state as a naturally recovered one.
  */
private[casualtiesbelow] final class BoneHealingEffect extends LimbRecoveryEffect(0xede0c8) {

  override protected def restorePerTick: Double = {
    Consts.Regeneration.BoneHealingEffectPerTick
  }

  override protected def healthOf(stats: LimbSnapshot): Double = {
    if (stats.fractureRecoveryTicks.isPresent) 0.0 else LimbSnapshot.MaxValue
  }

  override protected def heal(state: MutableLimbState, restore: Double): Unit = {
    state.fractureRecoveryTicks.foreach { remaining =>
      val advanced = remaining - restore
      state.fractureRecoveryTicks =
        if (advanced <= 1.0) None
        else Some(advanced.toInt)
    }
  }
}
