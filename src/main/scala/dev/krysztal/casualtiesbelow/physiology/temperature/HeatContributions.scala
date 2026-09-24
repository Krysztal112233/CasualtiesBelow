package dev.krysztal.casualtiesbelow.physiology.temperature

import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.event.BodyHeatContributionCallback
import dev.krysztal.casualtiesbelow.api.event.BodyHeatContributionContext
import dev.krysztal.casualtiesbelow.internal.Consts

/** The built-in contributors of the heat balance: exercise heat on the direct channel and
  * evaporative cooling on the dissipative channel. Direct contact heat is damage-type driven and
  * lives in [[DamageContribution]].
  */
private[temperature] object HeatContributions {

  /** Registers every built-in contributor on the heat contribution event. */
  def register(): Unit = {
    BodyHeatContributionCallback.EVENT.register(ExerciseHeat)
    BodyHeatContributionCallback.EVENT.register(EvaporativeCooling)
  }

  /** Exercise heat: the shared exertion signal converts to direct-channel heat at the configured
    * rate per exhaustion unit.
    */
  private object ExerciseHeat extends BodyHeatContributionCallback {

    override def contribute(
        player: ServerPlayer,
        frame: BodyHeatContributionCallback.Frame,
        context: BodyHeatContributionContext
    ): Unit = {
      val exhaustionPerSecond = ExertionTracker.observe(player)
      if (exhaustionPerSecond > 0.0) {
        context.addDirect(
          exhaustionPerSecond * Consts.Temperature.ExerciseHeatPerExhaustionPerSecond
        )
      }
    }
  }

  /** Evaporative cooling: wet skin sheds heat in proportion to wetness and air dryness (a jungle
    * defeats sweat, a desert exploits it). This is an active dissipation term, so it travels on the
    * dissipative channel and pays the armor's surviving dissipation block.
    */
  private object EvaporativeCooling extends BodyHeatContributionCallback {

    override def contribute(
        player: ServerPlayer,
        frame: BodyHeatContributionCallback.Frame,
        context: BodyHeatContributionContext
    ): Unit = {
      if (frame.wetness > 0.0) {
        context.addDissipative(
          frame.wetness * frame.airDryness * Consts.Temperature.EvaporationCoolingPerMinute
        )
      }
    }
  }
}
