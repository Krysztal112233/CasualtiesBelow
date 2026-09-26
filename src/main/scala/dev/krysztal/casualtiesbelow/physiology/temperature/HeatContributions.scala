package dev.krysztal.casualtiesbelow.physiology.temperature

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties

import dev.krysztal.casualtiesbelow.api.event.BodyHeatContributionCallback
import dev.krysztal.casualtiesbelow.api.event.BodyHeatContributionCallback.Frame
import dev.krysztal.casualtiesbelow.api.event.BodyHeatContributionContext
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.TypeAlias.*
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*

/** The built-in contributors of the heat balance: exercise heat on the direct channel, evaporative
  * cooling on the dissipative channel, and ambient environment sources split across both. Direct
  * contact heat is damage-type driven and lives in [[HeatDamageContribution]].
  */
private[temperature] object HeatContributions {

  /** Registers every built-in contributor on the heat contribution event. */
  def register(): Unit = {
    BodyHeatContributionCallback.EVENT.register(ExerciseHeat)
    BodyHeatContributionCallback.EVENT.register(EvaporativeCooling)
    BodyHeatContributionCallback.EVENT.register(EnvironmentBlock)
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

  /** Ambient environment sources: blocks around the player radiate heat or cold by tag tier, with
    * lit-gated members (furnaces, candles, campfires) counting only while lit — a block without a
    * `LIT` property is treated as always on. Heat pays the direct channel and cold the dissipative
    * channel, so a heat source cannot cancel its own warmth. Doses fall off linearly with Manhattan
    * distance, reaching zero at the scan boundary.
    */
  private object EnvironmentBlock extends BodyHeatContributionCallback {
    import dev.krysztal.casualtiesbelow.api.CasualtiesBelowTags.Blocks.*

    private val (heatMapping, coldMapping) = (
      Map(
        HeatSource1 -> Consts.Temperature.BlockHeatContribute1PerMinute,
        HeatSource2 -> Consts.Temperature.BlockHeatContribute2PerMinute,
        HeatSource3 -> Consts.Temperature.BlockHeatContribute3PerMinute
      ),
      Map(
        ColdSource1 -> Consts.Temperature.BlockColdContribute1PerMinute,
        ColdSource2 -> Consts.Temperature.BlockColdContribute2PerMinute,
        ColdSource3 -> Consts.Temperature.BlockColdContribute3PerMinute
      )
    )

    /** Sums the tier doses of `states` matching `mapping`, weighted by linear Manhattan falloff
      * (full dose at `center`, zero at the scan boundary) and skipping lit-gated blocks that are
      * currently unlit.
      */
    private def doseOf(
        states: LazyList[(pos: BlockPos, state: BlockState)],
        center: BlockPos,
        mapping: Map[TagKey[Block], Double]
    ): Double = {
      val radius = Consts.Temperature.BlockTemperatureRadius
      states.flatMap { it =>
        mapping.keySet
          .find { tag =>
            it.state.is(tag) &&
            it.state.getValueOrElse[JBoolean](BlockStateProperties.LIT, true)
          }
          .map((_, math.max(0, radius + 1 - it.pos.distManhattan(center)) / (radius + 1.0)))
          .map { (tag, weight) =>
            mapping(tag) * weight
          }
      }.sum
    }

    override def contribute(
        player: ServerPlayer,
        frame: Frame,
        context: BodyHeatContributionContext
    ): Unit = {
      val center = player.blockPosition()
      val states = player
        .level()
        .loadedStatesAround(center, Consts.Temperature.BlockTemperatureRadius)

      val heat = doseOf(states, center, heatMapping)
      val cold = doseOf(states, center, coldMapping)

      if (heat > 0.0) context.addDirect(heat)
      if (cold > 0.0) context.addDissipative(cold)
    }

  }
}
