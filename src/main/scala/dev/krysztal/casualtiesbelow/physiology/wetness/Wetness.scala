package dev.krysztal.casualtiesbelow.physiology.wetness

import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.event.DryingBonusCallback
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*
import dev.krysztal.casualtiesbelow.physiology.dirtiness.Dirtiness
import dev.krysztal.casualtiesbelow.physiology.temperature.ExertionTracker
import dev.krysztal.casualtiesbelow.physiology.temperature.TemperatureCalc

/** Advances skin wetness once per player tick, driven by [[InjuryProgression.tickPlayer]] right
  * after the body-temperature step: body temperature feeds wetness (sweat and drying), never the
  * reverse.
  *
  * Wetness rises in water or rain and otherwise changes through sweat and temperature- and
  * air-dryness-driven drying. The fire-drying bonus affects wetness only.
  *
  * Must tick after [[dev.krysztal.casualtiesbelow.physiology.temperature.Temperature]] so sweat
  * reads this tick's freshly written core temperature.
  */
object Wetness {

  /** Registers the built-in drying-bonus listeners. The progression itself is driven from
    * [[InjuryProgression.tickPlayer]], so no tick event is registered here.
    */
  def register(): Unit = {
    DryingBonusCallback.EVENT.register(FireDryingBonus)
  }

  /** Advances the player's wetness by one tick. */
  private[casualtiesbelow] def tick(
      player: ServerPlayer,
      vitals: VitalsComponentImpl
  ): Unit = {
    val environment = sampleEnvironment(player)
    vitals.setWetness(
      WetnessCalc.nextWetness(
        vitals.wetness,
        wetnessDelta(player, environment, vitals.bodyTemperature)
      )
    )
  }

  private def sampleEnvironment(player: ServerPlayer): WetnessEnvironment = {
    val level = player.level()
    val pos = player.blockPosition()
    val biome = level.getBiome(pos).value()
    val immersed = player.isInWater

    WetnessEnvironment(
      apparentTemperature = TemperatureCalc.apparentTemperature(
        biome.mappedTemperature(pos, level.getSeaLevel),
        immersed
      ),
      airDryness = biome.airDryness,
      immersed = immersed,
      raining = !immersed && level.isRainingAt(pos)
    )
  }

  /** Wetness change from immersion, rain, sweat, and environmental drying. */
  private def wetnessDelta(
      player: ServerPlayer,
      environment: WetnessEnvironment,
      coreTemperature: Double
  ): Double = {
    val exertionPerSecond =
      if (!environment.immersed) ExertionTracker.exhaustionPerSecond(player.getUUID) else 0.0
    val sweatPerSecond =
      if (
        exertionPerSecond > 0.0 &&
        coreTemperature > Consts.Wetness.SweatCoreTempThreshold
      ) {
        Dirtiness.markSweating(player.getUUID)
        Consts.Wetness.SweatWetnessPerSecond * WetnessCalc.sweatRateFraction(
          exertionPerSecond,
          SprintExhaustionPerSecond
        )
      } else {
        0.0
      }
    if (environment.immersed) {
      Consts.Wetness.ImmersionWetnessPerSecond * Consts.SecondsPerTick
    } else if (environment.raining) {
      Consts.Wetness.RainWetnessPerSecond * Consts.SecondsPerTick + sweatPerSecond * Consts.SecondsPerTick
    } else {
      val dryingBonus = DryingBonusCallback.EVENT.invoker().dryingBonus(player)
      -Consts.Wetness.DryingCurveFormula.evaluate(
        environment.apparentTemperature + dryingBonus
      ) * environment.airDryness * Consts.SecondsPerTick + sweatPerSecond * Consts.SecondsPerTick
    }
  }

  private final case class WetnessEnvironment(
      apparentTemperature: Double,
      airDryness: Double,
      immersed: Boolean,
      raining: Boolean
  )

  /** Vanilla sprinting accrues exhaustion at ~0.56/s; it anchors the sweat exertion fraction. */
  private val SprintExhaustionPerSecond = 0.56

  /** Being on fire flash-dries: a large apparent-temperature bonus for the drying curve only. */
  private object FireDryingBonus extends DryingBonusCallback {
    override def dryingBonus(player: ServerPlayer): Double =
      if (player.isOnFire) {
        Consts.Wetness.FireDryingBonusDegrees
      } else {
        0.0
      }
  }
}
