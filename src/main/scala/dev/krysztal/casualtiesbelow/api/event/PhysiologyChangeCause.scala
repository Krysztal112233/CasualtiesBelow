package dev.krysztal.casualtiesbelow.api.event

import net.minecraft.resources.Identifier

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi

/** Built-in causes used by physiology transition events.
  *
  * Datapack-backed adrenaline grants use the matching adrenaline-rule identifier directly, so an
  * integration can distinguish individual configured stimuli without another registry.
  */
object PhysiologyChangeCause {
  val Progression: Identifier = CasualtiesBelowApi.id("progression")
  val AdrenalineDecay: Identifier = CasualtiesBelowApi.id("adrenaline_decay")
  val AdminEdit: Identifier = CasualtiesBelowApi.id("admin_edit")
  val AdrenalineEdit: Identifier = CasualtiesBelowApi.id("adrenaline_edit")
  val Recovery: Identifier = CasualtiesBelowApi.id("recovery")
  val Reset: Identifier = CasualtiesBelowApi.id("reset")
  val HypoxiaDeathProtection: Identifier = CasualtiesBelowApi.id("hypoxia_death_protection")
}
