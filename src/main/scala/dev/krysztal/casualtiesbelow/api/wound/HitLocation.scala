package dev.krysztal.casualtiesbelow.api.wound

import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.api.data.GameplayDataLookup
import dev.krysztal.casualtiesbelow.api.data.HitLocationData

/** Guesses the body part a hit landed on from hit geometry — vanilla damage carries no hit-location
  * information. Geometry and positionless-hit weights come from the matching `hit_location`
  * registry entry for the victim's entity type.
  */
object HitLocation {

  /** Picks the body part hit on `victim` by `source`. Falls back to a weighted random part when the
    * source carries no position information.
    */
  def pick(victim: LivingEntity, source: DamageSource): BodyPart = {
    val rules = GameplayDataLookup.hitLocation(victim.level().registryAccess(), victim)
    hitOrigin(source) match {
      case Some(origin) => locate(victim, origin, rules)
      case None         => randomPart(victim, rules)
    }
  }

  /** Where the hit came from: the direct attacker's eye position, falling back to the source
    * position (projectile launch point, effect source, ...).
    */
  private def hitOrigin(source: DamageSource): Option[Vec3] =
    Option(source.getDirectEntity)
      .map(attacker => new Vec3(attacker.getX, attacker.getEyeY, attacker.getZ))
      .orElse(Option(source.getSourcePosition))

  private def locate(
      victim: LivingEntity,
      origin: Vec3,
      rules: HitLocationData
  ): BodyPart = {
    val relativeHeight = (origin.y - victim.getY) / victim.getBbHeight
    val (lateral, forward) = leftness(victim, origin)

    if (relativeHeight >= rules.headAbove) {
      BodyPart.Head
    } else if (relativeHeight < rules.legsBelow) {
      if (lateral > 0.0) BodyPart.LegLeft
      else if (lateral < 0.0) BodyPart.LegRight
      else if (victim.getRandom.nextBoolean()) BodyPart.LegLeft
      else BodyPart.LegRight
    } else if (math.abs(lateral) > math.abs(forward)) {
      // Side hit: the arm on that side takes it instead of the torso.
      if (lateral > 0.0) BodyPart.ArmLeft else BodyPart.ArmRight
    } else {
      BodyPart.Torso
    }
  }

  /** Victim→attacker horizontal vector projected onto the victim's left axis and facing axis, as
    * `(lateral, forward)`; `lateral > 0` means the attacker stands to the victim's left.
    */
  private def leftness(victim: LivingEntity, origin: Vec3): (Double, Double) = {
    val dx = origin.x - victim.getX
    val dz = origin.z - victim.getZ
    val yaw = math.toRadians(victim.getYRot)
    // Vanilla facing at zero pitch is (-sin yaw, cos yaw); left is that rotated +90°.
    val forwardX = -math.sin(yaw)
    val forwardZ = math.cos(yaw)
    (dx * forwardZ - dz * forwardX, dx * forwardX + dz * forwardZ)
  }

  /** Weighted random part for hits without position information. Weight mass below one is treated
    * as a re-roll (implemented by scaling the roll to the defined mass).
    */
  private def randomPart(victim: LivingEntity, rules: HitLocationData): BodyPart = {
    val total = FallbackOrder.map(part => rules.fallbackWeights.getOrElse(part, 0.0)).sum
    if (total <= 0.0) return BodyPart.Torso

    val rollMass = if (math.abs(total - 1.0) <= 1.0e-9) 1.0 else total
    val roll = victim.getRandom.nextDouble() * rollMass
    var cumulative = 0.0
    FallbackOrder
      .find { part =>
        cumulative += rules.fallbackWeights.getOrElse(part, 0.0)
        roll < cumulative
      }
      .orElse(FallbackOrder.reverse.find(part => rules.fallbackWeights.getOrElse(part, 0.0) > 0.0))
      .getOrElse(BodyPart.Torso)
  }

  // This order preserves the previous default bands exactly.
  private val FallbackOrder = List(
    BodyPart.Torso,
    BodyPart.Head,
    BodyPart.ArmLeft,
    BodyPart.ArmRight,
    BodyPart.LegLeft,
    BodyPart.LegRight
  )
}
