package dev.krysztal.casualtiesbelow.physiology.temperature

import java.util.UUID

import scala.collection.mutable

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

import dev.krysztal.casualtiesbelow.api.event.BodyHeatContributionCallback
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataLookup
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataStore
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataStores
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*

/** Advances body temperature once per player tick ([[InjuryProgression.tickPlayer]]).
  *
  * Core temperature approaches an environment- and armor-adjusted equilibrium, with direct heat and
  * armor-scaled dissipation contributions. Skin wetness is a separate parameter owned by
  * [[dev.krysztal.casualtiesbelow.physiology.wetness.Wetness]]: temperature feeds wetness, never
  * the reverse.
  *
  * The recurrence uses seconds; contribution and config rates use °C/min.
  */
object Temperature {

  /** Registers the built-in heat contributors. The progression itself is driven from
    * [[InjuryProgression.tickPlayer]], so no tick event is registered here.
    */
  def register(): Unit = {
    HeatContributions.register()
    HeatDamageContribution.register()
    ServerPlayConnectionEvents.DISCONNECT.register { (handler, _) =>
      ExertionTracker.discard(handler.player.getUUID);
      HeatDamageContribution.discard(handler.player.getUUID);
    }
  }

  /** Advances the player's body temperature by one tick. */
  private[casualtiesbelow] def tick(
      player: ServerPlayer,
      vitals: VitalsComponentImpl
  ): Unit = {
    val environment = sampleEnvironment(player)
    val store = GameplayDataStores.server(player.level().getServer)
    val armor = armorThermalCoefficients(player, store)
    val frame = BodyHeatContributionCallback.Frame(armor.fireResistance)
    val heatContributions = collectHeatContributions(player, frame)

    val nextCore = calculateNextCoreTemperature(
      vitals.bodyTemperature,
      environment,
      armor,
      heatContributions
    )
    vitals.setBodyTemperature(nextCore)
  }

  private def sampleEnvironment(player: ServerPlayer): TemperatureEnvironment = {
    val level = player.level()
    val pos = player.blockPosition()
    val biome = level.getBiome(pos).value()
    val immersed = player.isInWater

    TemperatureEnvironment(
      apparentTemperature = TemperatureCalc.apparentTemperature(
        biome.mappedTemperature(pos, level.getSeaLevel),
        immersed
      ),
      immersed = immersed
    )
  }

  private def collectHeatContributions(
      player: ServerPlayer,
      frame: BodyHeatContributionCallback.Frame
  ): BodyHeatContributionCallback.Accumulator = {
    val context = new BodyHeatContributionCallback.Accumulator
    BodyHeatContributionCallback.EVENT
      .invoker()
      .contribute(player, frame, context)
    context
  }

  private def calculateNextCoreTemperature(
      coreTemperature: Double,
      environment: TemperatureEnvironment,
      armor: ArmorThermal,
      heatContributions: BodyHeatContributionCallback.Accumulator
  ): Double = {
    val (comfortLow, comfortHigh) = CasualtiesBelowConfig.environment.effectiveComfortBound
    val equilibrium = Consts.Temperature.ComfortBandFormula.evaluate(
      environment.apparentTemperature,
      comfortLow,
      comfortHigh,
      Consts.Temperature.ComfortSlope
    )
    val effectiveEquilibrium =
      Consts.Temperature.EffectiveTemperatureFormula.evaluate(
        equilibrium,
        armor.effectiveInsulation
      )

    TemperatureCalc.nextCoreTemperature(
      coreTemperature,
      effectiveEquilibrium,
      TemperatureCalc.approachRatePerSecond(
        CasualtiesBelowConfig.environment.tauAirMinutes.get(),
        environment.immersed,
        Consts.Temperature.ImmersionRateMultiplier
      ),
      TemperatureCalc.productionPerSecond(
        heatContributions.directTotal,
        heatContributions.dissipativeTotal,
        armor.effectiveDissipationBlock
      ),
      Consts.SecondsPerTick
    )
  }

  /** Coverage-weighted material coefficients over the four armor slots. */
  private def armorThermalCoefficients(
      player: ServerPlayer,
      store: GameplayDataStore
  ): ArmorThermal = {
    var insulation = 0.0
    var dissipationBlock = 0.0
    var fireResistance = 0.0
    ArmorSlotWeights.foreach { (slot, weight) =>
      val stack = player.getItemBySlot(slot)
      if (!stack.isEmpty) {
        val thermal = GameplayDataLookup.materialThermal(stack, store)
        insulation += weight * thermal.insulation
        dissipationBlock += weight * thermal.dissipationBlock
        fireResistance += weight * thermal.fireResistance
      }
    }
    ArmorThermal(insulation, dissipationBlock, fireResistance)
  }

  private final case class TemperatureEnvironment(
      apparentTemperature: Double,
      immersed: Boolean
  )

  /** The three armor coefficients one tick pass consumes. */
  private final case class ArmorThermal(
      effectiveInsulation: Double,
      effectiveDissipationBlock: Double,
      fireResistance: Double
  )

  /** Drops this player's exertion tracking state when progression is skipped (death,
    * creative/spectator).
    */
  private[casualtiesbelow] def discard(player: ServerPlayer): Unit = {
    ExertionTracker.discard(player.getUUID)
    HeatDamageContribution.discard(player.getUUID)
  }

  /** Armor coverage weights by body surface: chest > legs > head ≈ feet. */
  private val ArmorSlotWeights: List[(EquipmentSlot, Double)] = List(
    (EquipmentSlot.CHEST, 0.35),
    (EquipmentSlot.LEGS, 0.3),
    (EquipmentSlot.HEAD, 0.2),
    (EquipmentSlot.FEET, 0.15)
  )
}
