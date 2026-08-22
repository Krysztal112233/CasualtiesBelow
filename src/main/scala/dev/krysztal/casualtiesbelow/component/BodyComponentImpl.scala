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
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.body.BodyComponent
import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.api.body.LimbStats
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

final class BodyComponentImpl(val player: Player) extends BodyComponent {
  private val limbs =
    Map.from(BodyPart.values.map(_ -> LimbStats()))

  override def stats(part: BodyPart): LimbStats = limbs(part).copy()

  override def setStats(part: BodyPart, stats: LimbStats): Unit = {
    limbs(part) = stats.copy()
    reconcileMovementModifiers()
  }

  override def copyFrom(
      other: BodyComponent,
      registryLookup: HolderLookup.Provider
  ): Unit = {
    BodyPart.values.foreach { p => setStats(p, other.stats(p)) }
  }

  override def writeData(out: ValueOutput): Unit = {
    BodyPart.values.foreach { part =>
      val child = out.child(part.id)
      val s = limbs(part)
      child.putDouble(BodyComponentImpl.MuscleHealthKey, s.muscleHealth)
      child.putDouble(BodyComponentImpl.SkinIntegrityKey, s.skinIntegrity)
      s.fractureRecoveryTicks.foreach { t =>
        child.putInt(BodyComponentImpl.FractureRecoveryTicksKey, t)
      }
      s.infectionProgress.foreach { p =>
        child.putDouble(BodyComponentImpl.InfectionProgressKey, p)
      }
      child.putBoolean(BodyComponentImpl.DislocatedKey, s.dislocated)
      child.putDouble(BodyComponentImpl.ExternalBleedingRateKey, s.externalBleedingRate)
      child.putDouble(BodyComponentImpl.PainKey, s.pain)
    }
  }

  override def readData(in: ValueInput): Unit = {
    BodyPart.values.foreach { part =>
      in.child(part.id).ifPresent { child =>
        val s = limbs(part)
        s.muscleHealth = child.getDoubleOr(BodyComponentImpl.MuscleHealthKey, LimbStats.MaxValue)
        s.skinIntegrity = child.getDoubleOr(BodyComponentImpl.SkinIntegrityKey, LimbStats.MaxValue)
        s.fractureRecoveryTicks =
          child.getInt(BodyComponentImpl.FractureRecoveryTicksKey).toScala.map(_.intValue)
        s.infectionProgress = child
          .read(BodyComponentImpl.InfectionProgressKey, Codec.DOUBLE)
          .toScala
          .map(_.doubleValue)
        s.dislocated = child.getBooleanOr(BodyComponentImpl.DislocatedKey, false)
        s.externalBleedingRate = child.getDoubleOr(BodyComponentImpl.ExternalBleedingRateKey, 0.0)
        s.pain = child.getDoubleOr(BodyComponentImpl.PainKey, 0.0)
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

  // NBT keys (serialization implementation detail; not part of the public API)
  private val MuscleHealthKey = "muscle_health"
  private val SkinIntegrityKey = "skin_integrity"
  private val FractureRecoveryTicksKey = "fracture_recovery_ticks"
  private val InfectionProgressKey = "infection_progress"
  private val DislocatedKey = "dislocated"
  private val ExternalBleedingRateKey = "external_bleeding_rate"
  private val PainKey = "pain"

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
