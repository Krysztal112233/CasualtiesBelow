package dev.krysztal.casualtiesbelow.progression

/** Pure episode logic for the "Not Today" advancement: an episode arms while total external
  * bleeding runs at or above the near-maximum threshold, and completes when bleeding later returns
  * to zero. Kept free of game state so the transitions are unit-testable.
  */
object HemostasisEpisode {
  enum State {
    case Idle, Armed
  }

  /** Advances one episode state from the player's current total external bleeding rate.
    * `nearMaxRate` is the armed threshold (maxExternalBleedingRate × the configured fraction).
    * Returns the next state and whether the episode completed this tick — armed and bleeding now
    * fully stopped.
    *
    * A non-positive threshold disables arming entirely: with maxExternalBleedingRate at zero no
    * wound can bleed, so every tick would otherwise read as both near-maximum and stopped.
    */
  def next(state: State, totalBleedingRate: Double, nearMaxRate: Double): (State, Boolean) = {
    state match {
      case State.Idle =>
        if (nearMaxRate > 0.0 && totalBleedingRate >= nearMaxRate) (State.Armed, false)
        else (State.Idle, false)
      case State.Armed =>
        if (totalBleedingRate <= 0.0) (State.Idle, true)
        else (State.Armed, false)
    }
  }
}
