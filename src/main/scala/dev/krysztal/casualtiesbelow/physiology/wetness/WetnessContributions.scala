package dev.krysztal.casualtiesbelow.physiology.wetness

import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.event.WetnessContributionCallback
import dev.krysztal.casualtiesbelow.api.event.WetnessContributionCallback.Frame
import dev.krysztal.casualtiesbelow.api.event.WetnessContributionContext
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.physiology.dirtiness.Dirtiness

/** The built-in wetness contributors, registered once at mod init: immersion, rain, sweat, and
  * environmental drying. Wetting contributions are positive, drying negative.
  */
private[wetness] object WetnessContributions {

  /** Registers every built-in contributor on the wetness contribution event. */
  def register(): Unit = {
    WetnessContributionCallback.EVENT.register(ImmersionWetting)
    WetnessContributionCallback.EVENT.register(RainWetting)
    WetnessContributionCallback.EVENT.register(Sweat)
    WetnessContributionCallback.EVENT.register(EnvironmentalDrying)
    WetnessContributionCallback.EVENT.register(NaturalDrying)
  }

  /** Immersion soaks at a rate that dwarfs every other source. */
  private object ImmersionWetting extends WetnessContributionCallback {

    override def contribute(
        player: ServerPlayer,
        frame: WetnessContributionCallback.Frame,
        context: WetnessContributionContext
    ): Unit = {
      if (frame.immersed) {
        context.add(Consts.Wetness.ImmersionWetnessPerSecond)
      }
    }
  }

  /** Rain wets slowly; it never coincides with immersion (the frame's `raining` excludes it). */
  private object RainWetting extends WetnessContributionCallback {

    override def contribute(
        player: ServerPlayer,
        frame: WetnessContributionCallback.Frame,
        context: WetnessContributionContext
    ): Unit = {
      if (frame.raining) context.add(Consts.Wetness.RainWetnessPerSecond)
    }
  }

  /** Sweat requires a hot core and scales linearly with the excess temperature above the threshold;
    * exertion enters only indirectly, through exercise heat raising the core. Sweating also flags
    * the player for the dirtiness hygiene multiplier.
    */
  private object Sweat extends WetnessContributionCallback {

    override def contribute(
        player: ServerPlayer,
        frame: WetnessContributionCallback.Frame,
        context: WetnessContributionContext
    ): Unit = {
      if (!frame.immersed && frame.coreTemperature > Consts.Wetness.SweatCoreTempThreshold) {
        Dirtiness.markSweating(player.getUUID)
        context.add(
          WetnessCalc.sweatRatePerSecond(
            frame.coreTemperature,
            Consts.Wetness.SweatCoreTempThreshold,
            Consts.Wetness.SweatWetnessPerDegreePerSecond
          )
        )
      }
    }
  }

  /** Environmental drying follows an exponential curve in apparent temperature, scaled by air
    * dryness; being on fire flash-dries via a large temperature bonus to the same curve.
    */
  private object EnvironmentalDrying extends WetnessContributionCallback {
    import Consts.Wetness.DryingCurveFormula

    override def contribute(
        player: ServerPlayer,
        frame: WetnessContributionCallback.Frame,
        context: WetnessContributionContext
    ): Unit = {
      if (!frame.immersed && !frame.raining) {
        val fireBonus =
          if (player.isOnFire) Consts.Wetness.FireDryingBonusDegrees
          else 0.0

        context.add(
          -DryingCurveFormula.evaluate(frame.apparentTemperature + fireBonus) * frame.airDryness
        )
      }
    }
  }

  private object NaturalDrying extends WetnessContributionCallback {
    override def contribute(
        player: ServerPlayer,
        frame: Frame,
        context: WetnessContributionContext
    ): Unit = {
      if (!frame.immersed && !frame.raining)
        context.add(-Consts.Wetness.NaturalDrynessPerSecond)
    }
  }

}
