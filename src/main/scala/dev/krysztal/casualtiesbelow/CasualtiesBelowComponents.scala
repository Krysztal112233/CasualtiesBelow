package dev.krysztal.casualtiesbelow

import scala.reflect.ClassTag

import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Player

import dev.krysztal.casualtiesbelow.component.BodyComponent
import dev.krysztal.casualtiesbelow.component.BodyComponentImpl
import dev.krysztal.casualtiesbelow.component.VitalsComponent
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl

import org.ladysnake.cca.api.v3.component.ComponentFactory
import org.ladysnake.cca.api.v3.component.ComponentKey
import org.ladysnake.cca.api.v3.component.ComponentRegistryV3
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer
import org.ladysnake.cca.api.v3.entity.RespawnCopyStrategy
import org.ladysnake.cca.api.v8.component.CardinalComponent

/** Registers the mod's Cardinal Components on players. Declared as the `cardinal-components-entity`
  * entrypoint in fabric.mod.json.
  */
object CasualtiesBelowComponents extends EntityComponentInitializer {

  val Body: ComponentKey[BodyComponent] = ofComponent("body")
  val Vitals: ComponentKey[VitalsComponent] = ofComponent("vitals")

  override def registerEntityComponentFactories(
      registry: EntityComponentFactoryRegistry
  ): Unit = {
    registry.registerForPlayers(
      Body,
      (player: Player) => BodyComponentImpl(player),
      RespawnCopyStrategy.ALWAYS_COPY
    )
    registry.registerForPlayers(
      Vitals,
      (player: Player) => VitalsComponentImpl(player),
      RespawnCopyStrategy.ALWAYS_COPY
    )
  }
}

private def ofComponent[T <: CardinalComponent](
    path: String
)(using c: ClassTag[T]): ComponentKey[T] = {
  // ClassTag.runtimeClass is typed Class[?]; safe to narrow: the tag of a component
  // trait carries that exact interface class.
  val componentClass = c.runtimeClass.asInstanceOf[Class[T]]
  ComponentRegistryV3.INSTANCE.getOrCreate(
    CasualtiesBelow.ofIdentifier(path),
    componentClass
  )
}
