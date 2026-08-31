package dev.krysztal.casualtiesbelow.api.body

/** Immutable, language-neutral view of blood oxygen and blood volume in mL. Blood oxygen is
  * normalized oxygen availability relative to a healthy, fully oxygenated player rather than a
  * clinical saturation percentage: moderate blood loss retains full carrying capacity, then
  * capacity falls linearly below the configured blood fraction.
  */
final case class CirculationSnapshot(bloodOxygen: Double, bloodVolume: Double)
