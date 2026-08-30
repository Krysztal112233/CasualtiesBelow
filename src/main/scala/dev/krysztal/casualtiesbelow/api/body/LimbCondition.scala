package dev.krysztal.casualtiesbelow.api.body

/** Discrete limb conditions whose onset is an event of its own, unlike continuous stats such as
  * muscle health and pain. Each onset grants a one-time pain injection separate from impact pain.
  */
enum LimbCondition {
  case Fracture, Dislocation
}
