package dev.krysztal.casualtiesbelow.component

import net.minecraft.world.entity.player.Player

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents

import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer
import org.ladysnake.cca.api.v3.entity.RespawnCopyStrategy

/** Internal Fabric entrypoint that binds the public component interfaces to their implementations.
  */
object CasualtiesBelowComponentInitializer extends EntityComponentInitializer {
  override def registerEntityComponentFactories(
      registry: EntityComponentFactoryRegistry
  ): Unit = {
    registry.registerForPlayers(
      CasualtiesBelowComponents.Body,
      (player: Player) => BodyComponentImpl(player),
      RespawnCopyStrategy.LOSSLESS_ONLY
    )
    registry.registerForPlayers(
      CasualtiesBelowComponents.Vitals,
      (player: Player) => VitalsComponentImpl(player),
      RespawnCopyStrategy.LOSSLESS_ONLY
    )
  }
}
