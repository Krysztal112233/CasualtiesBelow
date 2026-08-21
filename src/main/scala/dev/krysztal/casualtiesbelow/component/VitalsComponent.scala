package dev.krysztal.casualtiesbelow.component

import net.minecraft.core.HolderLookup
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.storage.{ValueInput, ValueOutput}

import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

import org.ladysnake.cca.api.v3.component.CopyableComponent
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent

/** Whole-player vitals: immune health value, consciousness, and blood volume. Immune health ranges
  * from 0 to the configured maximum (`maxImmuneHealth`, default 200), consciousness from 0 to
  * [[VitalsComponent.MaxValue]]; blood volume is in mL, up to the configured maximum. `Double`
  * rather than `Float` for the same reason as [[dev.krysztal.casualtiesbelow.component.LimbStats]]:
  * per-tick accumulation precision.
  */
trait VitalsComponent extends CopyableComponent[VitalsComponent] with AutoSyncedComponent {
  var immuneHealth: Double
  var consciousness: Double
  var bloodVolume: Double
}

object VitalsComponent {
  val MaxValue: Double = 100.0

  // NBT keys
  val ImmuneHealthKey = "immune_health"
  val ConsciousnessKey = "consciousness"
  val BloodVolumeKey = "blood_volume"
}

final class VitalsComponentImpl(val player: Player) extends VitalsComponent {
  var immuneHealth: Double = CasualtiesBelowConfig.MaxImmuneHealth.get()
  var consciousness: Double = VitalsComponent.MaxValue
  var bloodVolume: Double = CasualtiesBelowConfig.MaxBloodVolume.get()

  override def copyFrom(
      other: VitalsComponent,
      registryLookup: HolderLookup.Provider
  ): Unit = {
    immuneHealth = other.immuneHealth
    consciousness = other.consciousness
    bloodVolume = other.bloodVolume
  }

  override def writeData(out: ValueOutput): Unit = {
    out.putDouble(VitalsComponent.ImmuneHealthKey, immuneHealth)
    out.putDouble(VitalsComponent.ConsciousnessKey, consciousness)
    out.putDouble(VitalsComponent.BloodVolumeKey, bloodVolume)
  }

  override def readData(in: ValueInput): Unit = {
    immuneHealth =
      in.getDoubleOr(VitalsComponent.ImmuneHealthKey, CasualtiesBelowConfig.MaxImmuneHealth.get())
    consciousness = in.getDoubleOr(VitalsComponent.ConsciousnessKey, VitalsComponent.MaxValue)
    bloodVolume =
      in.getDoubleOr(VitalsComponent.BloodVolumeKey, CasualtiesBelowConfig.MaxBloodVolume.get())
  }
}
