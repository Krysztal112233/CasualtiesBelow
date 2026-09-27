package dev.krysztal.casualtiesbelow.physiology.wetness

import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.event.WetnessContributionCallback
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*
import dev.krysztal.casualtiesbelow.physiology.temperature.TemperatureCalc

/** Advances skin wetness once per player tick, driven by [[InjuryProgression.tickPlayer]] right
  * after the body-temperature step: body temperature feeds wetness (through the frame's core
  * temperature), never the reverse.
  *
  * The tick samples the environment into a frame, fires [[WetnessContributionCallback]] for the
  * built-in and external contributors, then applies the summed per-second rate clamped to the 0..1
  * axis. Must tick after [[dev.krysztal.casualtiesbelow.physiology.temperature.Temperature]] so the
  * frame carries this tick's freshly written core temperature.
  */
object Wetness {

  /** Registers the built-in wetness contributors. The progression itself is driven from
    * [[InjuryProgression.tickPlayer]], so no tick event is registered here.
    */
  def register(): Unit = {
    WetnessContributions.register()
  }

  /** Advances the player's wetness by one tick. */
  private[casualtiesbelow] def tick(
      player: ServerPlayer,
      vitals: VitalsComponentImpl
  ): Unit = {
    val frame = sampleFrame(player, vitals.bodyTemperature)
    val context = new WetnessContributionCallback.Accumulator
    WetnessContributionCallback.EVENT.invoker().contribute(player, frame, context)
    vitals.setWetness(
      WetnessCalc.nextWetness(vitals.wetness, context.totalPerSecond * Consts.SecondsPerTick)
    )
  }

  private def sampleFrame(
      player: ServerPlayer,
      coreTemperature: Double
  ): WetnessContributionCallback.Frame = {
    val level = player.level()
    val pos = player.blockPosition()
    val biome = level.getBiome(pos).value()
    val immersed = player.isInWater

    WetnessContributionCallback.Frame(
      coreTemperature = coreTemperature,
      apparentTemperature = TemperatureCalc.apparentTemperature(
        biome.mappedTemperature(pos, level.getSeaLevel),
        immersed
      ),
      airDryness = biome.airDryness,
      immersed = immersed,
      raining = !immersed && level.isRainingAt(pos)
    )
  }
}
