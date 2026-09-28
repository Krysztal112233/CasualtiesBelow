package dev.krysztal.casualtiesbelow.tweaks.effects

import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.internal.Consts.Tweaks.Effects
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*
import dev.krysztal.casualtiesbelow.physiology.circulation.BloodVolume

/** Behavior grafted onto vanilla's Regeneration effect: for players, each pulse is re-expressed as
  * blood-volume recovery instead of vanilla heart healing, which the mod bypasses entirely. The
  * vanilla pulse cadence (50 ticks shifted right by the amplifier) is preserved by the caller.
  */
private[casualtiesbelow] object RegenerationEffect {

  /** Runs one vanilla Regeneration pulse for a player. Like vanilla, the per-pulse amount is fixed
    * and `amplification` only tightens the pulse cadence upstream, so it is unused here.
    */
  def onPulse(player: ServerPlayer, amplification: Int): Unit = {
    val vitals = player.vitals
    BloodVolume.restore(
      vitals,
      Effects.RegenerationBloodGenerationPerPulse,
      BloodVolume.effectiveMaximum(vitals)
    )
  }
}
