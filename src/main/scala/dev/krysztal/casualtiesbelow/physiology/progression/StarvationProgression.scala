package dev.krysztal.casualtiesbelow.physiology.progression

import java.util.UUID

import scala.collection.mutable

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Difficulty
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.physiology.blood.BloodVolume

/** Translates accepted vanilla starvation pulses into bounded blood loss.
  *
  * [[dev.krysztal.casualtiesbelow.mixin.FoodDataMixin]] lets an eligible `FoodData.tick` pulse run
  * through vanilla so armor/effects, combat side effects, and Fabric's `AFTER_DAMAGE` event retain
  * their normal ownership. [[onAfterDamage]] records only successful `minecraft:starve` pulses as a
  * bounded UUID-keyed amount. [[InjuryProgression]] consumes that transient amount later in the
  * same server tick, before external bleeding, and owns the final fatal-source decision.
  */
object StarvationProgression {

  /** Whether `FoodData.tick` should invoke its vanilla starvation hurt call. Pulses stop before the
    * call once blood reaches the custom difficulty floor, and never run for physiology-frozen game
    * modes or Peaceful difficulty.
    */
  def shouldApplyVanillaPulse(player: ServerPlayer): Boolean = {
    if (player.isCreative || player.isSpectator || !player.isAlive) return false
    if (player.level().getDifficulty == Difficulty.PEACEFUL) return false

    val vitals = CasualtiesBelowComponents.Vitals.get(player)
    val maximum = BloodVolume.effectiveMaximum(vitals)
    val current = BloodVolume.normalizedVolume(vitals.circulation.bloodVolume, maximum)
    current > bloodFloor(player, maximum)
  }

  /** Queues one accepted starvation pulse from Fabric `AFTER_DAMAGE`. No player reference is
    * retained, and each UUID's aggregate is capped to the largest representable incoming float.
    */
  def onAfterDamage(
      player: ServerPlayer,
      source: DamageSource,
      damageTaken: Float
  ): Unit = {
    if (player.isCreative || player.isSpectator || !player.isAlive) return
    if (!source.is(DamageTypes.STARVE)) return
    if (!damageTaken.isFinite || damageTaken <= 0.0f) return

    val id = player.getUUID
    val accumulated = queuedDamage.getOrElse(id, 0.0)
    queuedDamage.update(
      id,
      (accumulated + damageTaken.toDouble).min(MaximumQueuedDamagePerPlayer)
    )
  }

  /** Consumes this player's queued pulses and applies their linear blood loss at the current
    * difficulty floor. Peaceful remains lossless even if difficulty changed after the callback.
    */
  private[progression] def consume(
      player: ServerPlayer,
      vitals: VitalsComponentImpl,
      maximum: Double
  ): StarvationResult = {
    val damage = queuedDamage.remove(player.getUUID).getOrElse(0.0)
    if (damage <= 0.0 || player.level().getDifficulty == Difficulty.PEACEFUL) {
      return StarvationResult()
    }

    val fraction =
      CasualtiesBelowConfig.StarvationBloodLossFractionPerDamage.get().doubleValue.max(0.0).min(1.0)
    val requestedLoss = BloodVolume.healthyMaximum * fraction * damage
    val drained = BloodVolume.drain(vitals, requestedLoss, maximum, bloodFloor(player, maximum))
    StarvationResult(
      changed = drained > 0.0,
      reachedZero = drained > 0.0 && vitals.circulation.bloodVolume <= 0.0
    )
  }

  /** Discards a queued pulse when progression is skipped for a frozen or dead player. */
  private[progression] def discard(player: ServerPlayer): Unit = {
    queuedDamage.remove(player.getUUID)
  }

  /** Drops entries for players who disconnected before the end-of-tick consumer ran. */
  private[progression] def discardRemaining(): Unit = {
    queuedDamage.clear()
  }

  /** Difficulty floor as a fraction of effective (post-sepsis) maximum blood. Normal is clamped no
    * higher than Easy at use time so malformed/reloaded relationships remain monotonic.
    */
  private def bloodFloor(player: ServerPlayer, maximum: Double): Double = {
    val easyFraction =
      CasualtiesBelowConfig.EasyStarvationBloodFloorFraction.get().doubleValue.max(0.0).min(1.0)
    val normalFraction =
      CasualtiesBelowConfig.NormalStarvationBloodFloorFraction
        .get()
        .doubleValue
        .max(0.0)
        .min(easyFraction)

    val fraction = player.level().getDifficulty match {
      case Difficulty.EASY   => easyFraction
      case Difficulty.NORMAL => normalFraction
      case _                 => 0.0
    }
    BloodVolume.normalizedVolume(maximum * fraction, maximum)
  }

  private val queuedDamage = mutable.HashMap.empty[UUID, Double]
  private val MaximumQueuedDamagePerPlayer = Float.MaxValue.toDouble
}

private[progression] final case class StarvationResult(
    changed: Boolean = false,
    reachedZero: Boolean = false
)
