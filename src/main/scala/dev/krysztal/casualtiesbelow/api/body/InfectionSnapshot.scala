package dev.krysztal.casualtiesbelow.api.body

/** Immutable, language-neutral view of immune health and sepsis load. Sepsis compresses the
  * effective blood-volume ceiling as it rises.
  */
final case class InfectionSnapshot(immuneHealth: Double, sepsis: Double)
