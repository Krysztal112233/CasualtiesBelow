package dev.krysztal.casualtiesbelow.api

import net.minecraft.resources.Identifier

/** Stable namespace and identifier helpers for integrations with Casualties: Below. */
object CasualtiesBelowApi {
  val ModId: String = "casualtiesbelow"

  def id(path: String): Identifier = Identifier.fromNamespaceAndPath(ModId, path)
}
