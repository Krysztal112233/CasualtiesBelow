package dev.krysztal.casualtiesbelow.component

import scala.collection.mutable.Map
import scala.jdk.OptionConverters.*

import com.mojang.serialization.Codec
import net.minecraft.core.HolderLookup
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.storage.{ValueInput, ValueOutput}

import org.ladysnake.cca.api.v3.component.CopyableComponent
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent

/** Per-limb stats.
  *
  * `muscleHealth` and `skinIntegrity` range from 0 to [[LimbStats.MaxValue]].
  *
  * `fractureRecoveryTicks` is `None` when the limb is not fractured; a value is the number of ticks
  * remaining until the fracture heals. `infectionProgress` is `None` when the limb is not infected;
  * a value is the infection progress from 0 to [[LimbStats.MaxValue]]. `externalBleedingRate` is
  * the amount of blood lost per tick in mL and has no upper bound.
  */
final case class LimbStats(
    var muscleHealth: Float = LimbStats.MaxValue,
    var skinIntegrity: Float = LimbStats.MaxValue,
    var fractureRecoveryTicks: Option[Int] = None,
    var infectionProgress: Option[Float] = None,
    var dislocated: Boolean = false,
    var externalBleedingRate: Double = 0.0
)

object LimbStats {
  val MaxValue: Float = 100f

  // NBT keys
  val MuscleHealthKey = "muscle_health"
  val SkinIntegrityKey = "skin_integrity"
  val FractureRecoveryTicksKey = "fracture_recovery_ticks"
  val InfectionProgressKey = "infection_progress"
  val DislocatedKey = "dislocated"
  val ExternalBleedingRateKey = "external_bleeding_rate"
}

/** Per-limb body condition (muscle health, skin integrity, fracture, infection, dislocation and
  * external bleeding) attached to every player.
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
      s.fractureRecoveryTicks.foreach { t =>
        child.putInt(LimbStats.FractureRecoveryTicksKey, t)
      }
      s.infectionProgress.foreach { p =>
        child.putFloat(LimbStats.InfectionProgressKey, p)
      }
      child.putBoolean(LimbStats.DislocatedKey, s.dislocated)
      child.putDouble(LimbStats.ExternalBleedingRateKey, s.externalBleedingRate)
    }
  }

  override def readData(in: ValueInput): Unit = {
    BodyPart.values.foreach { part =>
      in.child(part.id).ifPresent { child =>
        val s = limbs(part)
        s.muscleHealth = child.getFloatOr(LimbStats.MuscleHealthKey, LimbStats.MaxValue)
        s.skinIntegrity = child.getFloatOr(LimbStats.SkinIntegrityKey, LimbStats.MaxValue)
        s.fractureRecoveryTicks =
          child.getInt(LimbStats.FractureRecoveryTicksKey).toScala.map(_.intValue)
        s.infectionProgress =
          child.read(LimbStats.InfectionProgressKey, Codec.FLOAT).toScala.map(_.floatValue)
        s.dislocated = child.getBooleanOr(LimbStats.DislocatedKey, false)
        s.externalBleedingRate = child.getDoubleOr(LimbStats.ExternalBleedingRateKey, 0.0)
      }
    }
  }
}
