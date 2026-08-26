package dev.krysztal.casualtiesbelow.api.body

/** Server-authored phase of one pain-shock episode.
  *
  * [[Stable]] accumulates hidden shock load without changing consciousness. Crossing the configured
  * collapse threshold enters [[Collapsed]], which forces consciousness to literal zero. Once the
  * load returns to that threshold from above, [[Recovering]] permits consciousness to rise from
  * zero; the episode returns to [[Stable]] only after the player wakes.
  */
enum PainShockStage(val id: String) {
  case Stable extends PainShockStage("stable")
  case Collapsed extends PainShockStage("collapsed")
  case Recovering extends PainShockStage("recovering")
}

object PainShockStage {
  private val ById: Map[String, PainShockStage] = values.map(stage => stage.id -> stage).toMap

  def byId(id: String): Option[PainShockStage] = ById.get(id)
}
