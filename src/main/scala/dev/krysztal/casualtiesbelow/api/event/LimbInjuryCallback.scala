package dev.krysztal.casualtiesbelow.api.event

import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.player.Player

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory

import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.api.body.LimbCondition

/** Context of a single limb injury about to be applied. Carried as one object so new fields can be
  * added without breaking listener signatures.
  *
  * @param player
  *   the injured player (injuries are only attributed to players, server-side)
  * @param part
  *   the body part about to be injured
  * @param source
  *   the vanilla damage source (e.g. fall); listeners can filter by type/tags
  * @param damage
  *   the severity in half-hearts, as computed by the mod's damage formula
  * @param condition
  *   the discrete condition this injury onset applies, or `None` for a plain impact injury (no
  *   condition onset). Fracture/dislocation onsets are injuries of their own and fire this event
  *   with their condition set.
  * @param pain
  *   the pain this injury application will grant the limb (capped at the limb maximum on
  *   application). Listeners may adjust it before returning — including to zero. Defaults: impact
  *   injuries scale with the damage; condition onsets grant a fixed one-time amount.
  */
final case class LimbInjuryContext(
    player: Player,
    part: BodyPart,
    source: DamageSource,
    damage: Double,
    condition: Option[LimbCondition],
    var pain: Double
)

/** Fired before an injury is applied to a limb, once per affected limb. Return `false` to cancel
  * the injury for that limb (other limbs are unaffected).
  *
  * Server-side only. Listeners must not mutate the player's body component re-entrantly.
  */
trait LimbInjuryCallback {
  def onLimbInjury(context: LimbInjuryContext): Boolean
}

object LimbInjuryCallback {
  val EVENT: Event[LimbInjuryCallback] = EventFactory.createArrayBacked(
    classOf[LimbInjuryCallback],
    (listeners: Array[LimbInjuryCallback]) =>
      (context: LimbInjuryContext) => listeners.forall(_.onLimbInjury(context))
  )
}
