package dev.krysztal.casualtiesbelow.api.event

import java.util.Optional

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory

import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.api.body.LimbCondition
import dev.krysztal.casualtiesbelow.api.body.LimbSnapshot

/** Immutable description of one limb injury before it is applied.
  *
  * `basePain` has already been reduced by the adrenaline granted for the enclosing trauma, but has
  * not received random jitter or the limb-maximum cap. Provenance fields are empty for mechanisms
  * that did not originate from a datapack wound contribution.
  */
final class LimbInjuryContext(
    val player: ServerPlayer,
    val part: BodyPart,
    val source: DamageSource,
    val damage: Double,
    val condition: Optional[LimbCondition],
    val basePain: Double,
    val ruleId: Optional[Identifier],
    val profileId: Optional[Identifier],
    val applicationType: Optional[Identifier],
    val role: Optional[String]
)

/** Called before one limb injury is committed. Return `false` to reject only that injury. All
  * registered listeners run even if an earlier listener rejects it.
  */
trait LimbInjuryAllowCallback {
  def allowLimbInjury(context: LimbInjuryContext): Boolean
}

object LimbInjuryAllowCallback {
  val EVENT: Event[LimbInjuryAllowCallback] = EventFactory.createArrayBacked(
    classOf[LimbInjuryAllowCallback],
    (listeners: Array[LimbInjuryAllowCallback]) =>
      (context: LimbInjuryContext) => {
        var allowed = true
        listeners.foreach { listener =>
          if (!listener.allowLimbInjury(context)) allowed = false
        }
        allowed
      }
  )
}

/** Immutable result of one committed limb injury. `grantedPain` is the actual limb pain increase
  * after jitter and the maximum cap, not merely the requested amount.
  */
final class LimbInjuryAppliedContext(
    val injury: LimbInjuryContext,
    val before: LimbSnapshot,
    val after: LimbSnapshot,
    val grantedPain: Double
)

/** Fired after one limb injury has been committed. This event is observational. */
trait LimbInjuryAppliedCallback {
  def onLimbInjuryApplied(context: LimbInjuryAppliedContext): Unit
}

object LimbInjuryAppliedCallback {
  val EVENT: Event[LimbInjuryAppliedCallback] = EventFactory.createArrayBacked(
    classOf[LimbInjuryAppliedCallback],
    (listeners: Array[LimbInjuryAppliedCallback]) =>
      (context: LimbInjuryAppliedContext) => listeners.foreach(_.onLimbInjuryApplied(context))
  )
}
