package dev.krysztal.casualtiesbelow.api.body

import net.minecraft.world.entity.player.Player

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi
import dev.krysztal.casualtiesbelow.api.body.limb.BodyComponent
import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent

import org.ladysnake.cca.api.v3.component.ComponentKey
import org.ladysnake.cca.api.v3.component.ComponentRegistryV3
import org.ladysnake.cca.api.v8.component.CardinalComponent

/** Public component keys and read-only access helpers for the `limb` and `vitals` contracts.
  * Component registration and implementation live outside the API package.
  */
object CasualtiesBelowComponents {

  val Body: ComponentKey[BodyComponent] = componentKey("body", classOf[BodyComponent])
  val Vitals: ComponentKey[VitalsComponent] = componentKey("vitals", classOf[VitalsComponent])

  def body(player: Player): BodyComponent = Body.get(player)
  def vitals(player: Player): VitalsComponent = Vitals.get(player)

  private def componentKey[T <: CardinalComponent](
      path: String,
      componentClass: Class[T]
  ): ComponentKey[T] = {
    ComponentRegistryV3.INSTANCE.getOrCreate(
      CasualtiesBelowApi.id(path),
      componentClass
    )
  }
}
