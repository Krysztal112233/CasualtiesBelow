package dev.krysztal.casualtiesbelow.effect

import scala.jdk.CollectionConverters.*
import scala.util.boundary
import scala.util.boundary.break

import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi

/** Registers and synchronizes the mod's status effects. */
private[casualtiesbelow] object CasualtiesBelowEffects {
  private var vitalsEffectSynchronizers: Vector[VitalsEffectSynchronizer] = Vector.empty
  private var tick = 0;

  private[casualtiesbelow] val OpioidAnalgesia: Holder[MobEffect] =
    register(
      "opioid_analgesia",
      CasualtiesBelowCustomEffect(
        MobEffectCategory.NEUTRAL,
        16646020,
        opioidAnalgesiaAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val OpioidDependence: Holder[MobEffect] =
    register(
      "opioid_dependence",
      CasualtiesBelowCustomEffect(
        MobEffectCategory.HARMFUL,
        0x8f4961,
        opioidDependenceAmplifierFromVitals
      )
    )

  def register(): Unit = {
    ServerTickEvents.START_SERVER_TICK.register { server =>
      boundary[Unit] {
        tick += 1
        if (tick < 20) break(())

        tick = 0
        server
          .getPlayerList()
          .getPlayers()
          .asScala
          .foreach(player => synchronizeFromVitals(player))
      }
    }
  }

  private[casualtiesbelow] def synchronizeFromVitals(player: ServerPlayer): Unit = {
    vitalsEffectSynchronizers.foreach(_.synchronizeFromVitals(player))
  }

  private def register(
      name: String,
      mobEffect: CasualtiesBelowCustomEffect
  ): Holder[MobEffect] = {
    val holder: Holder[MobEffect] = Registry.registerForHolder(
      BuiltInRegistries.MOB_EFFECT,
      CasualtiesBelowApi.id(name),
      mobEffect
    )
    mobEffect.bindEffectHolder(holder)
    vitalsEffectSynchronizers = vitalsEffectSynchronizers :+ mobEffect

    holder
  }
}
