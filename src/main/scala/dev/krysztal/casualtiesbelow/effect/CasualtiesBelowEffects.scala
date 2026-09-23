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
  private[casualtiesbelow] val Hypoxia: Holder[MobEffect] =
    register(
      "hypoxia",
      CasualtiesBelowCustomEffect(
        MobEffectCategory.HARMFUL,
        0x6579a8,
        hypoxiaAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val Hypovolemia: Holder[MobEffect] =
    register(
      "hypovolemia",
      CasualtiesBelowCustomEffect(
        MobEffectCategory.HARMFUL,
        0x9f3441,
        hypovolemiaAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val Sepsis: Holder[MobEffect] =
    register(
      "sepsis",
      CasualtiesBelowCustomEffect(
        MobEffectCategory.HARMFUL,
        0x9b587a,
        sepsisAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val Hypothermia: Holder[MobEffect] =
    register(
      "hypothermia",
      CasualtiesBelowCustomEffect(
        MobEffectCategory.HARMFUL,
        0x5dadd6,
        hypothermiaAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val Hyperthermia: Holder[MobEffect] =
    register(
      "hyperthermia",
      CasualtiesBelowCustomEffect(
        MobEffectCategory.HARMFUL,
        0xd96b31,
        hyperthermiaAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val Unconsciousness: Holder[MobEffect] =
    register(
      "unconsciousness",
      CasualtiesBelowCustomEffect(
        MobEffectCategory.HARMFUL,
        0x404058,
        unconsciousnessAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val PainShock: Holder[MobEffect] =
    register(
      "pain_shock",
      CasualtiesBelowCustomEffect(
        MobEffectCategory.HARMFUL,
        0x762838,
        painShockAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val Alertness: Holder[MobEffect] =
    register(
      "alertness",
      CasualtiesBelowCustomEffect(
        MobEffectCategory.BENEFICIAL,
        0xe4c44b,
        alertnessAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val Wetness: Holder[MobEffect] =
    register(
      "wetness",
      CasualtiesBelowCustomEffect(
        MobEffectCategory.HARMFUL,
        0x3a8fa8,
        wetnessAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val Dirtiness: Holder[MobEffect] =
    register(
      "dirtiness",
      CasualtiesBelowCustomEffect(
        MobEffectCategory.HARMFUL,
        0x785638,
        dirtinessAmplifierFromVitals
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
