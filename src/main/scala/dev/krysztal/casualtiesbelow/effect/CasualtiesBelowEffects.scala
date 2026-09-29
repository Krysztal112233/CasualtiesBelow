package dev.krysztal.casualtiesbelow.effect

import scala.compiletime.ops.boolean
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
import dev.krysztal.casualtiesbelow.internal.extensions.MinecraftServerExtensions.*

/** Registers and synchronizes the mod's status effects. */
private[casualtiesbelow] object CasualtiesBelowEffects {
  private var vitalsEffectSynchronizers: Vector[VitalsEffectSynchronizer] = Vector.empty
  private var tick = 0;

  private[casualtiesbelow] val OpioidAnalgesia: Holder[MobEffect] =
    register(
      "opioid_analgesia",
      CasualtiesBelowDisplayOnlyEffect(
        MobEffectCategory.NEUTRAL,
        16646020,
        opioidAnalgesiaAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val OpioidDependence: Holder[MobEffect] =
    register(
      "opioid_dependence",
      CasualtiesBelowDisplayOnlyEffect(
        MobEffectCategory.HARMFUL,
        0x8f4961,
        opioidDependenceAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val Hypoxia: Holder[MobEffect] =
    register(
      "hypoxia",
      CasualtiesBelowDisplayOnlyEffect(
        MobEffectCategory.HARMFUL,
        0x6579a8,
        hypoxiaAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val Hypovolemia: Holder[MobEffect] =
    register(
      "hypovolemia",
      CasualtiesBelowDisplayOnlyEffect(
        MobEffectCategory.HARMFUL,
        0x9f3441,
        hypovolemiaAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val BloodLoss: Holder[MobEffect] =
    register(
      "blood_loss",
      CasualtiesBelowDisplayOnlyEffect(
        MobEffectCategory.HARMFUL,
        0x9c3b49,
        bloodLossAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val Sepsis: Holder[MobEffect] =
    register(
      "sepsis",
      CasualtiesBelowDisplayOnlyEffect(
        MobEffectCategory.HARMFUL,
        0x9b587a,
        sepsisAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val Hypothermia: Holder[MobEffect] =
    register(
      "hypothermia",
      CasualtiesBelowDisplayOnlyEffect(
        MobEffectCategory.HARMFUL,
        0x5dadd6,
        hypothermiaAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val Hyperthermia: Holder[MobEffect] =
    register(
      "hyperthermia",
      CasualtiesBelowDisplayOnlyEffect(
        MobEffectCategory.HARMFUL,
        0xd96b31,
        hyperthermiaAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val Unconsciousness: Holder[MobEffect] =
    register(
      "unconsciousness",
      CasualtiesBelowDisplayOnlyEffect(
        MobEffectCategory.HARMFUL,
        0x404058,
        unconsciousnessAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val PainShock: Holder[MobEffect] =
    register(
      "pain_shock",
      CasualtiesBelowDisplayOnlyEffect(
        MobEffectCategory.HARMFUL,
        0x762838,
        painShockAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val Alertness: Holder[MobEffect] =
    register(
      "alertness",
      CasualtiesBelowDisplayOnlyEffect(
        MobEffectCategory.BENEFICIAL,
        0xe4c44b,
        alertnessAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val Wetness: Holder[MobEffect] =
    register(
      "wetness",
      CasualtiesBelowDisplayOnlyEffect(
        MobEffectCategory.HARMFUL,
        0x3a8fa8,
        wetnessAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val Dirtiness: Holder[MobEffect] =
    register(
      "dirtiness",
      CasualtiesBelowDisplayOnlyEffect(
        MobEffectCategory.HARMFUL,
        0x785638,
        dirtinessAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val Fracture: Holder[MobEffect] =
    register(
      "fracture",
      CasualtiesBelowDisplayOnlyEffect(
        MobEffectCategory.HARMFUL,
        0xd6d2c4,
        fractureAmplifierFromVitals
      )
    )
  private[casualtiesbelow] val Dislocation: Holder[MobEffect] =
    register(
      "dislocation",
      CasualtiesBelowDisplayOnlyEffect(
        MobEffectCategory.HARMFUL,
        0x8a7fb8,
        dislocationAmplifierFromVitals
      )
    )

  def register(): Unit = {
    ServerTickEvents.START_SERVER_TICK.register { server =>
      boundary[Unit] {
        tick += 1
        if (tick < 20) break(())

        tick = 0
        server.getPlayers
          .foreach(player => synchronizeFromVitals(player))
      }
    }
  }

  private[casualtiesbelow] def synchronizeFromVitals(player: ServerPlayer): Unit = {
    vitalsEffectSynchronizers.foreach(_.synchronizeFromVitals(player))
  }

  private def register(
      name: String,
      mobEffect: CasualtiesBelowDisplayOnlyEffect,
      forDisplay: Boolean = true
  ): Holder[MobEffect] = {
    val holder: Holder[MobEffect] = Registry.registerForHolder(
      BuiltInRegistries.MOB_EFFECT,
      CasualtiesBelowApi.id(name),
      mobEffect
    )
    mobEffect.bindEffectHolder(holder)

    if (forDisplay)
      vitalsEffectSynchronizers = vitalsEffectSynchronizers :+ mobEffect

    holder
  }
}
