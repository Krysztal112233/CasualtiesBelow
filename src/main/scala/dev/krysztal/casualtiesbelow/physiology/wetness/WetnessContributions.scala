package dev.krysztal.casualtiesbelow.physiology.wetness

import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.event.WetnessContributionCallback
import dev.krysztal.casualtiesbelow.api.event.WetnessContributionContext
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.physiology.dirtiness.Dirtiness
import dev.krysztal.casualtiesbelow.physiology.temperature.ExertionTracker

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
      if (frame.raining) {
        context.add(Consts.Wetness.RainWetnessPerSecond)
      }
    }
  }

  /** Sweat requires both a hot core and actual exertion, then scales linearly with exertion up to
    * the vanilla sprint reference. Sweating also flags the player for the dirtiness hygiene
    * multiplier.
    */
  private object Sweat extends WetnessContributionCallback {

    override def contribute(
        player: ServerPlayer,
        frame: WetnessContributionCallback.Frame,
        context: WetnessContributionContext
    ): Unit = {
      if (!frame.immersed && frame.coreTemperature > Consts.Wetness.SweatCoreTempThreshold) {
        // Read-only access: ExertionTracker.observe has EMA side effects and is called once per
        // tick by the heat-contribution pass.
        val exertionPerSecond = ExertionTracker.exhaustionPerSecond(player.getUUID)
        if (exertionPerSecond > 0.0) {
          Dirtiness.markSweating(player.getUUID)
          context.add(
            Consts.Wetness.SweatWetnessPerSecond * WetnessCalc.sweatRateFraction(
              exertionPerSecond,
              SprintExhaustionPerSecond
            )
          )
        }
      }
    }
  }

  /** Environmental drying follows an exponential curve in apparent temperature, scaled by air
    * dryness; being on fire flash-dries via a large temperature bonus to the same curve.
    */
  private object EnvironmentalDrying extends WetnessContributionCallback {

    override def contribute(
        player: ServerPlayer,
        frame: WetnessContributionCallback.Frame,
        context: WetnessContributionContext
    ): Unit = {
      if (!frame.immersed && !frame.raining) {
        val fireBonus =
          if (player.isOnFire) {
            Consts.Wetness.FireDryingBonusDegrees
          } else {
            0.0
          }
        context.add(
          -Consts.Wetness.DryingCurveFormula.evaluate(frame.apparentTemperature + fireBonus) *
            frame.airDryness
        )
      }
    }
  }

  /** Vanilla sprinting accrues exhaustion at ~0.56/s; it anchors the sweat exertion fraction. */
  private val SprintExhaustionPerSecond = 0.56
}
