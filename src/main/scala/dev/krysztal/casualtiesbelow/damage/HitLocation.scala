package dev.krysztal.casualtiesbelow.damage

import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

import dev.krysztal.casualtiesbelow.component.BodyPart

/** Guesses the body part a hit landed on from hit geometry — vanilla damage carries no hit-location
  * information.
  *
  * Height bands from the hit height relative to the victim's bounding box: below [[LegsBelow]]
  * legs, at or above [[HeadAbove]] head, in between torso — or, when the hit came from the side,
  * the arm on that side. Left/right is the sign of the victim→attacker vector projected onto the
  * victim's left axis.
  */
object HitLocation {

  /** Picks the body part hit on `victim` by `source`. Falls back to a weighted random part when the
    * source carries no position information.
    */
  def pick(victim: LivingEntity, source: DamageSource): BodyPart =
    hitOrigin(source) match {
      case Some(origin) => locate(victim, origin)
      case None         => randomPart(victim)
    }

  /** Where the hit came from: the direct attacker's eye position, falling back to the source
    * position (projectile launch point, effect source, ...).
    */
  private def hitOrigin(source: DamageSource): Option[Vec3] =
    Option(source.getDirectEntity)
      .map(attacker => new Vec3(attacker.getX, attacker.getEyeY, attacker.getZ))
      .orElse(Option(source.getSourcePosition))

  private def locate(victim: LivingEntity, origin: Vec3): BodyPart = {
    val relativeHeight = (origin.y - victim.getY) / victim.getBbHeight
    val (lateral, forward) = leftness(victim, origin)

    if (relativeHeight >= HeadAbove) {
      BodyPart.Head
    } else if (relativeHeight < LegsBelow) {
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

  /** Weighted random part for hits without position information: torso 50%, head 10%, each limb
    * 10%.
    */
  private def randomPart(victim: LivingEntity): BodyPart = {
    val roll = victim.getRandom.nextDouble()
    if (roll < 0.5) {
      BodyPart.Torso
    } else if (roll < 0.6) {
      BodyPart.Head
    } else {
      val limbs = List(BodyPart.ArmLeft, BodyPart.ArmRight, BodyPart.LegLeft, BodyPart.LegRight)
      limbs(math.min(((roll - 0.6) / 0.1).toInt, limbs.size - 1))
    }
  }

  /** Relative hit height below which the hit lands on the legs. */
  private val LegsBelow = 0.35

  /** Relative hit height at or above which the hit lands on the head: the hit must come from at
    * least the top of the victim's bounding box, so only elevated attackers reach it. Same-level
    * humanoid melee sits below it (player eye 0.90, zombie eye 0.97 of the victim's height).
    */
  private val HeadAbove = 1.0
}
