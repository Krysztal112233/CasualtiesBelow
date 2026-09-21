package dev.krysztal.casualtiesbelow.internal.extension

import net.minecraft.world.entity.player.Player

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.component.BodyComponentImpl
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl

/** Enrichments over [Player] giving server-authoritative code typed component access.
  *
  * Fails fast on unexpected component implementations instead of silently degrading, mirroring the
  * behavior of the former `ComponentAccess` helpers.
  */
private[casualtiesbelow] object PlayerExtensions {

  extension (player: Player) {

    /** The player's body component implementation; fails fast on unexpected implementations. */
    def body: BodyComponentImpl = CasualtiesBelowComponents.Body.get(player) match {
      case component: BodyComponentImpl => component
      case component                    =>
        throw IllegalStateException(
          s"Unexpected body component implementation: ${component.getClass.getName}"
        )
    }

    /** The player's vitals component implementation; fails fast on unexpected implementations. */
    def vitals: VitalsComponentImpl = CasualtiesBelowComponents.Vitals.get(player) match {
      case component: VitalsComponentImpl => component
      case component                      =>
        throw IllegalStateException(
          s"Unexpected vitals component implementation: ${component.getClass.getName}"
        )
    }
  }
}
