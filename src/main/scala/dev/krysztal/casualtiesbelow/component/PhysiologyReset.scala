package dev.krysztal.casualtiesbelow.component

import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.extension.ConfigValueExtensions.*
import dev.krysztal.casualtiesbelow.internal.extension.PlayerExtensions.*
import dev.krysztal.casualtiesbelow.physiology.adrenaline.Adrenaline
import dev.krysztal.casualtiesbelow.physiology.bleeding.TotemHemostasis
import dev.krysztal.casualtiesbelow.physiology.pain.PainShock
import dev.krysztal.casualtiesbelow.physiology.progression.ConsciousnessProgression
import dev.krysztal.casualtiesbelow.physiology.progression.HypoxiaProgression

/** Atomic administrative reset of body and whole-player physiology. */
object PhysiologyReset {
  def reset(player: ServerPlayer): Unit = {
    BodyMutations.reset(player)

    val vitals = player.vitals
    VitalsMutations.setImmuneHealth(vitals, CasualtiesBelowConfig.MaxImmuneHealth.value)
    VitalsMutations.setBloodOxygen(vitals, VitalsComponent.MaxBloodOxygen)
    VitalsMutations.setBloodVolume(vitals, CasualtiesBelowConfig.MaxBloodVolume.value)
    HypoxiaProgression.reset(vitals)
    TotemHemostasis.reset(vitals)
    VitalsMutations.setSepsis(vitals, 0.0)
    VitalsMutations.setDiscomfort(vitals, 0.0)
    VitalsMutations.setDirtiness(vitals, 0.0)
    VitalsMutations.setOpioidLevel(vitals, 0.0)
    VitalsMutations.setOpioidDependence(vitals, 0.0)
    VitalsMutations.setBodyTemperature(vitals, VitalsComponent.NormalBodyTemperature)
    VitalsMutations.setWetness(vitals, 0.0)
    Adrenaline.reset(player, vitals)
    Adrenaline.discard(player)
    PainShock.resetHealthy(player, vitals)
    ConsciousnessProgression.resetHealthy(player, vitals)

    BodyMutations.syncNow(player)
    VitalsMutations.syncNow(player)
  }
}
