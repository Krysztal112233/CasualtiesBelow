package dev.krysztal.casualtiesbelow.component

import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart

/** Internal anatomical groups and adjacency used by physiology implementation code. */
private[casualtiesbelow] object BodyTopology {
  val Legs: List[BodyPart] = List(BodyPart.LegLeft, BodyPart.LegRight)

  /** Infection contagion uses a torso-centered star: an infection in an extremity must pass through
    * the torso before reaching another extremity.
    */
  val Adjacent: Map[BodyPart, List[BodyPart]] = Map(
    BodyPart.Head -> List(BodyPart.Torso),
    BodyPart.Torso -> List(
      BodyPart.Head,
      BodyPart.ArmLeft,
      BodyPart.ArmRight,
      BodyPart.LegLeft,
      BodyPart.LegRight
    ),
    BodyPart.ArmLeft -> List(BodyPart.Torso),
    BodyPart.ArmRight -> List(BodyPart.Torso),
    BodyPart.LegLeft -> List(BodyPart.Torso),
    BodyPart.LegRight -> List(BodyPart.Torso)
  )
}
