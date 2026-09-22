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
import dev.krysztal.casualtiesbelow.api.body.limb.BodyComponent
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.api.body.limb.LimbSnapshot
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

import org.ladysnake.cca.api.v3.component.CopyableComponent
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent

final class BodyComponentImpl(val player: Player)
    extends BodyComponent
    with CopyableComponent[BodyComponent]
    with AutoSyncedComponent {
  private val limbs =
    Map.from(BodyPart.values.map(_ -> MutableLimbState()))

  override def stats(part: BodyPart): LimbSnapshot = limbs(part).snapshot

  private[casualtiesbelow] def mutableCopy(part: BodyPart): MutableLimbState = limbs(part).copy()

  private[casualtiesbelow] def replace(part: BodyPart, state: MutableLimbState): Unit = {
    limbs(part) = MutableLimbState.normalize(state)
    reconcileMovementModifiers()
  }

  override def copyFrom(
      other: BodyComponent,
      registryLookup: HolderLookup.Provider
  ): Unit = {
    BodyPart.values.foreach { part => replace(part, MutableLimbState.from(other.stats(part))) }
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
        s.muscleHealth = child.getDoubleOr(BodyComponentImpl.MuscleHealthKey, LimbSnapshot.MaxValue)
        s.skinIntegrity =
          child.getDoubleOr(BodyComponentImpl.SkinIntegrityKey, LimbSnapshot.MaxValue)
        s.fractureRecoveryTicks =
          child.getInt(BodyComponentImpl.FractureRecoveryTicksKey).toScala.map(_.intValue)
        s.infectionProgress = child
          .read(BodyComponentImpl.InfectionProgressKey, Codec.DOUBLE)
          .toScala
          .map(_.doubleValue)
        s.dislocated = child.getBooleanOr(BodyComponentImpl.DislocatedKey, false)
        s.externalBleedingRate = child.getDoubleOr(BodyComponentImpl.ExternalBleedingRateKey, 0.0)
        s.pain = child.getDoubleOr(BodyComponentImpl.PainKey, 0.0)
        limbs(part) = MutableLimbState.normalize(s)
      }
    }
    reconcileMovementModifiers()
  }

  /** Recomputes the transient attribute modifiers derived from leg conditions. Fracture and
    * dislocation form one structural layer: a dislocated leg counts as one share of the configured
    * fractions, a fractured leg as [[BodyComponentImpl.FracturePenaltyMultiplier]] shares, and a
    * leg with both contributes both shares. Muscle damage forms a separate multiplicative layer:
    * each leg's normalized deficit is squared, then both legs are averaged.
    *
    * Only the server derives these modifiers; vanilla's syncable attributes carry the authoritative
    * values to clients. Fixed ids and value comparisons make reconciliation idempotent, while the
    * continuously changing muscle layer is quantized to avoid per-tick attribute packets during
    * slow natural regeneration. Creative and spectator players retain their limb state but suppress
    * both layers until they return to survival or adventure mode.
    */
  private[casualtiesbelow] def reconcileMovementModifiers(): Unit = {
    if (player.level().isClientSide()) return

    val restrictionsApply = !player.isCreative && !player.isSpectator
    val structuralSeverity =
      if (!restrictionsApply) {
        0.0
      } else {
        BodyTopology.Legs.foldLeft(0.0) { (total, p) =>
          val s = limbs(p)
          val fractureShare =
            if (s.fractureRecoveryTicks.isDefined) BodyComponentImpl.FracturePenaltyMultiplier
            else 0.0
          val dislocationShare = if (s.dislocated) 1.0 else 0.0
          total + fractureShare + dislocationShare
        }
      }
    val muscleDeficit =
      if (!restrictionsApply) {
        0.0
      } else {
        BodyTopology.Legs.foldLeft(0.0) { (total, p) =>
          val healthFraction =
            (limbs(p).muscleHealth / LimbSnapshot.MaxValue).max(0.0).min(1.0)
          val deficit = 1.0 - healthFraction
          total + deficit * deficit
        } / BodyTopology.Legs.size
      }

    reconcileAttribute(
      Attributes.MOVEMENT_SPEED,
      BodyComponentImpl.LegSpeedPenaltyId,
      -structuralSeverity * CasualtiesBelowConfig.movement.dislocationSpeedReduction.get()
    )
    reconcileAttribute(
      Attributes.JUMP_STRENGTH,
      BodyComponentImpl.LegJumpPenaltyId,
      -structuralSeverity * CasualtiesBelowConfig.movement.dislocationJumpReduction.get()
    )
    reconcileAttribute(
      Attributes.MOVEMENT_SPEED,
      BodyComponentImpl.LegMuscleSpeedPenaltyId,
      BodyComponentImpl.quantizeMusclePenalty(
        muscleDeficit * CasualtiesBelowConfig.movement.muscleSpeedReduction.get()
      )
    )
    reconcileAttribute(
      Attributes.JUMP_STRENGTH,
      BodyComponentImpl.LegMuscleJumpPenaltyId,
      BodyComponentImpl.quantizeMusclePenalty(
        muscleDeficit * CasualtiesBelowConfig.movement.muscleJumpReduction.get()
      )
    )
  }

  private def reconcileAttribute(
      attribute: Holder[Attribute],
      id: Identifier,
      amount: Double
  ): Unit = {
    Option(player.getAttribute(attribute)).foreach { instance =>
      val current = Option(instance.getModifier(id))
      if (amount == 0.0) {
        if (current.isDefined) instance.removeModifier(id)
      } else if (
        !current
          .exists(m => m.amount() == amount && m.operation() == Operation.ADD_MULTIPLIED_TOTAL)
      ) {
        instance.addOrUpdateTransientModifier(
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
  private val MuscleModifierQuantum = 0.001

  private def quantizeMusclePenalty(reduction: Double): Double =
    -math.round(reduction / MuscleModifierQuantum) * MuscleModifierQuantum

  val LegSpeedPenaltyId: Identifier =
    CasualtiesBelow.ofIdentifier("leg_speed_penalty")
  val LegJumpPenaltyId: Identifier =
    CasualtiesBelow.ofIdentifier("leg_jump_penalty")
  val LegMuscleSpeedPenaltyId: Identifier =
    CasualtiesBelow.ofIdentifier("leg_muscle_speed_penalty")
  val LegMuscleJumpPenaltyId: Identifier =
    CasualtiesBelow.ofIdentifier("leg_muscle_jump_penalty")
}
