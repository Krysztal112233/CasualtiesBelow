package dev.krysztal.casualtiesbelow.component

import net.minecraft.world.entity.player.Player

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents

/** Typed access to component implementations for server-authoritative mutation code. */
private[casualtiesbelow] object ComponentAccess {
  def body(player: Player): BodyComponentImpl =
    CasualtiesBelowComponents.Body.get(player) match {
      case component: BodyComponentImpl => component
      case component                    =>
        throw IllegalStateException(
          s"Unexpected body component implementation: ${component.getClass.getName}"
        )
    }

  def vitals(player: Player): VitalsComponentImpl =
    CasualtiesBelowComponents.Vitals.get(player) match {
      case component: VitalsComponentImpl => component
      case component                      =>
        throw IllegalStateException(
          s"Unexpected vitals component implementation: ${component.getClass.getName}"
        )
    }
}
