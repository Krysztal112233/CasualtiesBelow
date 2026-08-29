package dev.krysztal.casualtiesbelow.api.body

/** Discrete limb conditions whose onset is an event of its own, unlike the continuous stats (muscle
  * health, pain, ...). Each onset grants a one-time pain injection (see
  * [[dev.krysztal.casualtiesbelow.damage.WoundApplications]]), separate from impact pain.
  */
enum LimbCondition {
  case Fracture, Dislocation
}
