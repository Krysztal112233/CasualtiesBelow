package dev.krysztal.casualtiesbelow.component

import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*
import dev.krysztal.casualtiesbelow.physiology.adrenaline.Adrenaline
import dev.krysztal.casualtiesbelow.physiology.circulation.HypoxiaProgression
import dev.krysztal.casualtiesbelow.physiology.circulation.TotemHemostasis
import dev.krysztal.casualtiesbelow.physiology.consciousness.Consciousness
import dev.krysztal.casualtiesbelow.physiology.pain.PainShock

/** Atomic administrative reset of body and whole-player physiology. */
object PhysiologyReset {
  def reset(player: ServerPlayer): Unit = {
    BodyMutations.reset(player)

    val vitals = player.vitals
    vitals.setImmuneHealth(Consts.Immune.DefaultImmuneHealth)
    vitals.setBloodOxygen(VitalsComponent.MaxBloodOxygen)
    vitals.setBloodVolume(Consts.Vitals.MaxBloodVolume)
    HypoxiaProgression.reset(vitals)
    TotemHemostasis.reset(vitals)
    vitals.setSepsis(0.0)
    vitals.setDiscomfort(0.0)
    vitals.setDirtiness(0.0)
    vitals.setOpioidLevel(0.0)
    vitals.setOpioidDependence(0.0)
    vitals.setBodyTemperature(VitalsComponent.NormalBodyTemperature)
    vitals.setWetness(0.0)
    Adrenaline.reset(player, vitals)
    PainShock.resetHealthy(player, vitals)
    Consciousness.resetHealthy(player, vitals)
  }
}
