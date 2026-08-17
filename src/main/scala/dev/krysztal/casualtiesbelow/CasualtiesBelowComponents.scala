package dev.krysztal.casualtiesbelow

import dev.krysztal.casualtiesbelow.component.{
  BodyComponent,
  BodyComponentImpl,
  VitalsComponent,
  VitalsComponentImpl
}
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Player
import org.ladysnake.cca.api.v3.component.{ComponentFactory, ComponentKey, ComponentRegistryV3}
import org.ladysnake.cca.api.v3.entity.{
  EntityComponentFactoryRegistry,
  EntityComponentInitializer,
  RespawnCopyStrategy
}

/** Registers the mod's Cardinal Components on players. Declared as the `cardinal-components-entity`
  * entrypoint in fabric.mod.json.
  */
object CasualtiesBelowComponents extends EntityComponentInitializer {

  val Body: ComponentKey[BodyComponent] =
    ComponentRegistryV3.INSTANCE.getOrCreate(
      Identifier.fromNamespaceAndPath("casualtiesbelow", "body"),
      classOf[BodyComponent]
    )

  val Vitals: ComponentKey[VitalsComponent] =
    ComponentRegistryV3.INSTANCE.getOrCreate(
      Identifier.fromNamespaceAndPath("casualtiesbelow", "vitals"),
      classOf[VitalsComponent]
    )

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
