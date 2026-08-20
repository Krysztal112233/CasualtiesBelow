package dev.krysztal.casualtiesbelow.component

import scala.collection.mutable.Map
import scala.jdk.OptionConverters.*

import com.mojang.serialization.Codec
import net.minecraft.core.Holder
import net.minecraft.core.HolderLookup
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.storage.{ValueInput, ValueOutput}

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

import org.ladysnake.cca.api.v3.component.CopyableComponent
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent

/** Per-limb stats.
  *
  * `muscleHealth`, `skinIntegrity` and `pain` range from 0 to [[LimbStats.MaxValue]]. All numeric
  * stats are `Double`: they evolve continuously (per-tick accumulation), where `Float`'s 24-bit
  * mantissa would eventually swallow small deltas.
  *
  * `fractureRecoveryTicks` is `None` when the limb is not fractured; a value is the number of ticks
  * remaining until the fracture heals. `infectionProgress` is `None` when the limb is not infected;
  * a value is the infection progress from 0 to [[LimbStats.MaxValue]]. `externalBleedingRate` is
  * the amount of blood lost per tick in mL, capped proportionally to the skin damage (see
  * `InjuryProgression`).
  */
final case class LimbStats(
    var muscleHealth: Double = LimbStats.MaxValue,
    var skinIntegrity: Double = LimbStats.MaxValue,
    var fractureRecoveryTicks: Option[Int] = None,
    var infectionProgress: Option[Double] = None,
    var dislocated: Boolean = false,
    var externalBleedingRate: Double = 0.0,
    var pain: Double = 0.0
)

object LimbStats {
  val MaxValue: Double = 100.0

  // NBT keys
  val MuscleHealthKey = "muscle_health"
  val SkinIntegrityKey = "skin_integrity"
  val FractureRecoveryTicksKey = "fracture_recovery_ticks"
  val InfectionProgressKey = "infection_progress"
  val DislocatedKey = "dislocated"
  val ExternalBleedingRateKey = "external_bleeding_rate"
  val PainKey = "pain"
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
    reconcileMovementModifiers()
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
      child.putDouble(LimbStats.MuscleHealthKey, s.muscleHealth)
      child.putDouble(LimbStats.SkinIntegrityKey, s.skinIntegrity)
      s.fractureRecoveryTicks.foreach { t =>
        child.putInt(LimbStats.FractureRecoveryTicksKey, t)
      }
      s.infectionProgress.foreach { p =>
        child.putDouble(LimbStats.InfectionProgressKey, p)
      }
      child.putBoolean(LimbStats.DislocatedKey, s.dislocated)
      child.putDouble(LimbStats.ExternalBleedingRateKey, s.externalBleedingRate)
      child.putDouble(LimbStats.PainKey, s.pain)
    }
  }

  override def readData(in: ValueInput): Unit = {
    BodyPart.values.foreach { part =>
      in.child(part.id).ifPresent { child =>
        val s = limbs(part)
        s.muscleHealth = child.getDoubleOr(LimbStats.MuscleHealthKey, LimbStats.MaxValue)
        s.skinIntegrity = child.getDoubleOr(LimbStats.SkinIntegrityKey, LimbStats.MaxValue)
        s.fractureRecoveryTicks =
          child.getInt(LimbStats.FractureRecoveryTicksKey).toScala.map(_.intValue)
        s.infectionProgress =
          child.read(LimbStats.InfectionProgressKey, Codec.DOUBLE).toScala.map(_.doubleValue)
        s.dislocated = child.getBooleanOr(LimbStats.DislocatedKey, false)
        s.externalBleedingRate = child.getDoubleOr(LimbStats.ExternalBleedingRateKey, 0.0)
        s.pain = child.getDoubleOr(LimbStats.PainKey, 0.0)
      }
    }
    reconcileMovementModifiers()
  }

  /** Recomputes the transient attribute modifiers derived from leg conditions. Fracture and
    * dislocation are independent conditions and their penalties stack: a dislocated leg counts as
    * one share of the configured fractions, a fractured leg as
    * [[BodyComponentImpl.FracturePenaltyMultiplier]] shares (fracture is the worse condition), and
    * a leg with both contributes both shares. Remove-then-add with fixed modifier ids keeps
    * repeated calls idempotent, mirroring how vanilla applies the sprinting modifier.
    */
  private def reconcileMovementModifiers(): Unit = {
    val legSeverity = BodyPart.Legs.foldLeft(0.0) { (total, p) =>
      val s = limbs(p)
      val fractureShare =
        if (s.fractureRecoveryTicks.isDefined) BodyComponentImpl.FracturePenaltyMultiplier else 0.0
      val dislocationShare = if (s.dislocated) 1.0 else 0.0
      total + fractureShare + dislocationShare
    }
    reconcileAttribute(
      Attributes.MOVEMENT_SPEED,
      BodyComponentImpl.LegSpeedPenaltyId,
      -legSeverity * CasualtiesBelowConfig.DislocationSpeedReduction.get()
    )
    reconcileAttribute(
      Attributes.JUMP_STRENGTH,
      BodyComponentImpl.LegJumpPenaltyId,
      -legSeverity * CasualtiesBelowConfig.DislocationJumpReduction.get()
    )
  }

  private def reconcileAttribute(
      attribute: Holder[Attribute],
      id: Identifier,
      amount: Double
  ): Unit = {
    Option(player.getAttribute(attribute)).foreach { instance =>
      instance.removeModifier(id)
      if (amount != 0.0) {
        instance.addTransientModifier(
          new AttributeModifier(id, amount, Operation.ADD_MULTIPLIED_TOTAL)
        )
      }
    }
  }
}

object BodyComponentImpl {

  /** How many dislocated-leg shares a fractured leg contributes to the movement/jump penalties:
    * fracture is the worse condition, so it reuses the dislocation config fractions scaled up by
    * this fixed structural factor.
    */
  private val FracturePenaltyMultiplier = 1.5

  val LegSpeedPenaltyId: Identifier =
    CasualtiesBelow.ofIdentifier("leg_speed_penalty")
  val LegJumpPenaltyId: Identifier =
    CasualtiesBelow.ofIdentifier("leg_jump_penalty")
}
