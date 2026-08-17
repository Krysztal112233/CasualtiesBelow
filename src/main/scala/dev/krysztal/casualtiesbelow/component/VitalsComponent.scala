package dev.krysztal.casualtiesbelow.component

import net.minecraft.core.HolderLookup
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.storage.{ValueInput, ValueOutput}

import org.ladysnake.cca.api.v3.component.CopyableComponent
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent

/** Whole-player vitals: immune health value and consciousness. Both range from 0 to
  * [[VitalsComponent.MaxValue]].
  */
trait VitalsComponent extends CopyableComponent[VitalsComponent] with AutoSyncedComponent {
  var immuneHealth: Float
  var consciousness: Float
}

object VitalsComponent {
  val MaxValue: Float = 100f

  // NBT keys
  val ImmuneHealthKey = "immune_health"
  val ConsciousnessKey = "consciousness"
}

final class VitalsComponentImpl(val player: Player) extends VitalsComponent {
  var immuneHealth: Float = VitalsComponent.MaxValue
  var consciousness: Float = VitalsComponent.MaxValue

  override def copyFrom(
      other: VitalsComponent,
      registryLookup: HolderLookup.Provider
  ): Unit = {
    immuneHealth = other.immuneHealth
    consciousness = other.consciousness
  }

  override def writeData(out: ValueOutput): Unit = {
    out.putFloat(VitalsComponent.ImmuneHealthKey, immuneHealth)
    out.putFloat(VitalsComponent.ConsciousnessKey, consciousness)
  }

  override def readData(in: ValueInput): Unit = {
    immuneHealth = in.getFloatOr(VitalsComponent.ImmuneHealthKey, VitalsComponent.MaxValue)
    consciousness = in.getFloatOr(VitalsComponent.ConsciousnessKey, VitalsComponent.MaxValue)
  }
}
