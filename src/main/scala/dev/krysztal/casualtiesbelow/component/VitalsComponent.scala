package dev.krysztal.casualtiesbelow.component

import net.minecraft.core.HolderLookup
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.storage.{ValueInput, ValueOutput}

import org.ladysnake.cca.api.v3.component.CopyableComponent
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent

/** Whole-player vitals: immune health value and consciousness. Both range from 0 to
  * [[VitalsComponent.MaxValue]]. `Double` rather than `Float` for the same reason as
  * [[dev.krysztal.casualtiesbelow.component.LimbStats]]: per-tick accumulation precision.
  */
trait VitalsComponent extends CopyableComponent[VitalsComponent] with AutoSyncedComponent {
  var immuneHealth: Double
  var consciousness: Double
}

object VitalsComponent {
  val MaxValue: Double = 100.0

  // NBT keys
  val ImmuneHealthKey = "immune_health"
  val ConsciousnessKey = "consciousness"
}

final class VitalsComponentImpl(val player: Player) extends VitalsComponent {
  var immuneHealth: Double = VitalsComponent.MaxValue
  var consciousness: Double = VitalsComponent.MaxValue

  override def copyFrom(
      other: VitalsComponent,
      registryLookup: HolderLookup.Provider
  ): Unit = {
    immuneHealth = other.immuneHealth
    consciousness = other.consciousness
  }

  override def writeData(out: ValueOutput): Unit = {
    out.putDouble(VitalsComponent.ImmuneHealthKey, immuneHealth)
    out.putDouble(VitalsComponent.ConsciousnessKey, consciousness)
  }

  override def readData(in: ValueInput): Unit = {
    immuneHealth = in.getDoubleOr(VitalsComponent.ImmuneHealthKey, VitalsComponent.MaxValue)
    consciousness = in.getDoubleOr(VitalsComponent.ConsciousnessKey, VitalsComponent.MaxValue)
  }
}
