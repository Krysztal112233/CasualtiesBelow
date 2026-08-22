package dev.krysztal.casualtiesbelow.api.body

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
  val byId: Map[String, BodyPart] = values.map(p => p.id -> p).toMap

  val Legs: List[BodyPart] = List(LegLeft, LegRight)
  val Arms: List[BodyPart] = List(ArmLeft, ArmRight)

  /** Anatomical adjacency for infection contagion: a star with the torso as the hub, so an
    * infection in an extremity must pass through the torso to reach another extremity.
    */
  val Adjacent: Map[BodyPart, List[BodyPart]] = Map(
    Head -> List(Torso),
    Torso -> List(Head, ArmLeft, ArmRight, LegLeft, LegRight),
    ArmLeft -> List(Torso),
    ArmRight -> List(Torso),
    LegLeft -> List(Torso),
    LegRight -> List(Torso)
  )
}
