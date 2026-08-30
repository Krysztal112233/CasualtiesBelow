package dev.krysztal.casualtiesbelow.api.body

import java.util.Optional

import scala.jdk.OptionConverters.*

/** Player body parts tracked by the mod. Vanilla has no hit-location concept; damage is attributed
  * to a part by our own logic.
  */
enum BodyPart(val id: String) {
  case Head extends BodyPart("head")
  case Torso extends BodyPart("torso")
  case ArmLeft extends BodyPart("arm_left")
  case ArmRight extends BodyPart("arm_right")
  case LegLeft extends BodyPart("leg_left")
  case LegRight extends BodyPart("leg_right")
}

object BodyPart {
  private val ById: Map[String, BodyPart] = values.map(p => p.id -> p).toMap

  def fromId(id: String): Optional[BodyPart] = ById.get(id).toJava
}
