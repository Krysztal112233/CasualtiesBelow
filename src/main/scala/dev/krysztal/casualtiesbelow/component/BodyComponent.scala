package dev.krysztal.casualtiesbelow.component

import scala.collection.mutable.Map

import net.minecraft.core.HolderLookup
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.storage.{ValueInput, ValueOutput}

import org.ladysnake.cca.api.v3.component.CopyableComponent
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent

/** Per-limb stats. Both values range from 0 to [[LimbStats.MaxValue]]. */
final case class LimbStats(
    var muscleHealth: Float = LimbStats.MaxValue,
    var skinIntegrity: Float = LimbStats.MaxValue
)

object LimbStats {
  val MaxValue: Float = 100f

  // NBT keys
  val MuscleHealthKey = "muscle_health"
  val SkinIntegrityKey = "skin_integrity"
}

/** Per-limb body condition (muscle health + skin integrity) attached to every player.
  */
trait BodyComponent extends CopyableComponent[BodyComponent] with AutoSyncedComponent {
  def stats(part: BodyPart): LimbStats

  def setStats(part: BodyPart, stats: LimbStats): Unit
}

final class BodyComponentImpl(val player: Player) extends BodyComponent {
  private val limbs =
    Map.from(BodyPart.values.map(_ -> LimbStats()))

  override def stats(part: BodyPart): LimbStats = limbs(part)

  override def setStats(part: BodyPart, stats: LimbStats): Unit = {
    limbs(part) = stats
  }

  override def copyFrom(
      other: BodyComponent,
      registryLookup: HolderLookup.Provider
  ): Unit = {
    BodyPart.values.foreach { p => setStats(p, other.stats(p).copy()) }
  }

  override def writeData(out: ValueOutput): Unit = {
    BodyPart.values.foreach { part =>
      val child = out.child(part.id)
      val s = limbs(part)
      child.putFloat(LimbStats.MuscleHealthKey, s.muscleHealth)
      child.putFloat(LimbStats.SkinIntegrityKey, s.skinIntegrity)
    }
  }

  override def readData(in: ValueInput): Unit = {
    BodyPart.values.foreach { part =>
      in.child(part.id).ifPresent { child =>
        val s = limbs(part)
        s.muscleHealth = child.getFloatOr(LimbStats.MuscleHealthKey, LimbStats.MaxValue)
        s.skinIntegrity = child.getFloatOr(LimbStats.SkinIntegrityKey, LimbStats.MaxValue)
      }
    }
  }
}
