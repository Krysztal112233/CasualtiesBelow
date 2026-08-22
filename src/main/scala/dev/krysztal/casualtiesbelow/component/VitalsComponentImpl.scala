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
    out.putDouble(VitalsComponent.ImmuneHealthKey, immuneHealth)
    out.putDouble(VitalsComponent.ConsciousnessKey, consciousness)
    out.putDouble(VitalsComponent.BloodVolumeKey, bloodVolume)
    out.putDouble(VitalsComponent.SepsisKey, sepsis)
  }

  override def readData(in: ValueInput): Unit = {
    immuneHealth =
      in.getDoubleOr(VitalsComponent.ImmuneHealthKey, CasualtiesBelowConfig.MaxImmuneHealth.get())
    consciousness = in.getDoubleOr(VitalsComponent.ConsciousnessKey, VitalsComponent.MaxValue)
    bloodVolume =
      in.getDoubleOr(VitalsComponent.BloodVolumeKey, CasualtiesBelowConfig.MaxBloodVolume.get())
    sepsis = in.getDoubleOr(VitalsComponent.SepsisKey, 0.0)
  }
}
