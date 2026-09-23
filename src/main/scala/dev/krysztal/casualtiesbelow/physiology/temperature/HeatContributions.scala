package dev.krysztal.casualtiesbelow.physiology.temperature

import java.lang.{Boolean => JBoolean}

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.state.properties.BlockStateProperties

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowTags
import dev.krysztal.casualtiesbelow.api.event.BodyHeatContributionCallback
import dev.krysztal.casualtiesbelow.api.event.BodyHeatContributionContext
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

/** The built-in contributors of the heat balance: exercise and fire contact heat on the direct
  * channel, evaporative cooling on the dissipative channel.
  */
private[temperature] object HeatContributions {

  /** Registers every built-in contributor on the heat contribution event. */
  def register(): Unit = {
    BodyHeatContributionCallback.EVENT.register(ExerciseHeat)
    BodyHeatContributionCallback.EVENT.register(FireContactHeat)
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
          exhaustionPerSecond * CasualtiesBelowConfig.temperature.exerciseHeatPerExhaustionPerSecond
            .get()
        )
      }
    }
  }

  /** Fire/lava contact heat: direct contact transfers heat regardless of insulation (convection
    * clothing cannot stop conduction), so it goes to the direct channel in three tiers — lava
    * contact, being on fire, standing on a heat-source block — highest tier wins, tiers never stack
    * (vanilla also suppresses the on-fire damage-over-time while in lava). The armor's
    * fire-resistance coefficient (netherite's "doesn't burn" extension) reduces the tier here, at
    * the source, as the event contract requires.
    */
  private object FireContactHeat extends BodyHeatContributionCallback {

    override def contribute(
        player: ServerPlayer,
        frame: BodyHeatContributionCallback.Frame,
        context: BodyHeatContributionContext
    ): Unit = {
      val tier: Double =
        if (player.isInLava) {
          CasualtiesBelowConfig.temperature.lavaContactHeatPerMinute.get().doubleValue
        } else if (player.isOnFire) {
          CasualtiesBelowConfig.temperature.onFireHeatPerMinute.get().doubleValue
        } else if (standingOnHeatSource(player)) {
          CasualtiesBelowConfig.temperature.heatSourceBlockHeatPerMinute.get().doubleValue
        } else {
          0.0
        }
      if (tier > 0.0) {
        val resistance = frame.fireResistance
        context.addDirect(tier * (1.0 - resistance))
      }
    }

    /** The block at the feet and the one below; tag members with a LIT property (campfires) only
      * count while lit.
      */
    private def standingOnHeatSource(player: ServerPlayer): Boolean = {
      val level = player.level()
      val feet = player.blockPosition()
      List(feet, feet.below()).exists { pos =>
        val state = level.getBlockState(pos)
        state.is(CasualtiesBelowTags.HeatSourceBlocks) &&
        (!state.hasProperty(BlockStateProperties.LIT) ||
          state.getValue[JBoolean](BlockStateProperties.LIT).booleanValue())
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
          frame.wetness * frame.airDryness * CasualtiesBelowConfig.temperature.evaporationCoolingPerMinute
            .get()
        )
      }
    }
  }
}
