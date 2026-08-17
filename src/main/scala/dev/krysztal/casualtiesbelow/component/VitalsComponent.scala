package dev.krysztal.casualtiesbelow.component

import net.minecraft.core.HolderLookup
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.storage.{ValueInput, ValueOutput}
import org.ladysnake.cca.api.v3.component.CopyableComponent
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent

/** Whole-player vitals: overall health value and consciousness. Both range from 0 to
  * [[VitalsComponent.MaxValue]].
  */
trait VitalsComponent extends CopyableComponent[VitalsComponent] with AutoSyncedComponent {
  var health: Float
  var consciousness: Float
}

object VitalsComponent {
  val MaxValue: Float = 100f

  // NBT keys
  val HealthKey = "health"
  val ConsciousnessKey = "consciousness"
}

final class VitalsComponentImpl(val player: Player) extends VitalsComponent {
  var health: Float = VitalsComponent.MaxValue
  var consciousness: Float = VitalsComponent.MaxValue

  override def copyFrom(
      other: VitalsComponent,
      registryLookup: HolderLookup.Provider
  ): Unit = {
    health = other.health
    consciousness = other.consciousness
  }

  override def writeData(out: ValueOutput): Unit = {
    out.putFloat(VitalsComponent.HealthKey, health)
    out.putFloat(VitalsComponent.ConsciousnessKey, consciousness)
  }

  override def readData(in: ValueInput): Unit = {
    health = in.getFloatOr(VitalsComponent.HealthKey, VitalsComponent.MaxValue)
    consciousness = in.getFloatOr(VitalsComponent.ConsciousnessKey, VitalsComponent.MaxValue)
  }
}
