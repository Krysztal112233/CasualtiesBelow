package dev.krysztal.casualtiesbelow.api.body

/** Server-authored phase of one pain-shock episode.
  *
  * [[Stable]] accumulates hidden shock load without changing consciousness. Adrenaline can hold a
  * threshold-crossing player in [[Deferred]] while they remain awake; if load recovers below the
  * base threshold, that pending collapse is cancelled. Crossing the adrenaline-adjusted threshold
  * enters [[Collapsed]], which forces consciousness to literal zero. Once load returns to the base
  * threshold from above, [[Recovering]] permits consciousness to rise from zero; the episode
  * returns to [[Stable]] only after the player wakes.
  */
enum PainShockStage(val id: String) {
  case Stable extends PainShockStage("stable")
  case Collapsed extends PainShockStage("collapsed")
  case Recovering extends PainShockStage("recovering")
  // Appended to retain the ordinals of the three pre-adrenaline public enum cases.
  case Deferred extends PainShockStage("deferred")
}

object PainShockStage {
  private val ById: Map[String, PainShockStage] = values.map(stage => stage.id -> stage).toMap

  def byId(id: String): Option[PainShockStage] = ById.get(id)
}
