package dev.krysztal.casualtiesbelow.component

import net.minecraft.core.HolderLookup
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

import dev.krysztal.casualtiesbelow.api.body.VitalsComponent
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

final class VitalsComponentImpl(val player: Player) extends VitalsComponent {
  var immuneHealth: Double = CasualtiesBelowConfig.MaxImmuneHealth.get()
  var consciousness: Double = VitalsComponent.MaxValue
  var bloodVolume: Double = CasualtiesBelowConfig.MaxBloodVolume.get()
  var sepsis: Double = 0.0

  override def copyFrom(
      other: VitalsComponent,
      registryLookup: HolderLookup.Provider
  ): Unit = {
    immuneHealth = other.immuneHealth
    consciousness = other.consciousness
    bloodVolume = other.bloodVolume
    sepsis = other.sepsis
  }

  override def writeData(out: ValueOutput): Unit = {
    out.putDouble(VitalsComponentImpl.ImmuneHealthKey, immuneHealth)
    out.putDouble(VitalsComponentImpl.ConsciousnessKey, consciousness)
    out.putDouble(VitalsComponentImpl.BloodVolumeKey, bloodVolume)
    out.putDouble(VitalsComponentImpl.SepsisKey, sepsis)
  }

  override def readData(in: ValueInput): Unit = {
    immuneHealth = in.getDoubleOr(
      VitalsComponentImpl.ImmuneHealthKey,
      CasualtiesBelowConfig.MaxImmuneHealth.get()
    )
    consciousness = in.getDoubleOr(VitalsComponentImpl.ConsciousnessKey, VitalsComponent.MaxValue)
    bloodVolume =
      in.getDoubleOr(VitalsComponentImpl.BloodVolumeKey, CasualtiesBelowConfig.MaxBloodVolume.get())
    sepsis = in.getDoubleOr(VitalsComponentImpl.SepsisKey, 0.0)
  }
}

object VitalsComponentImpl {

  // NBT keys (serialization implementation detail; not part of the public API)
  private val ImmuneHealthKey = "immune_health"
  private val ConsciousnessKey = "consciousness"
  private val BloodVolumeKey = "blood_volume"
  private val SepsisKey = "sepsis"
}
