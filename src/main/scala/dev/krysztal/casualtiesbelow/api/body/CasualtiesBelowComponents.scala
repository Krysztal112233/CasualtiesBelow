package dev.krysztal.casualtiesbelow.api.body

import scala.reflect.ClassTag

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.component.BodyComponentImpl
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.pain.PainShock
import dev.krysztal.casualtiesbelow.progression.ConsciousnessProgression

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

  /** Resets a player to a fully healthy state: pristine limbs, full vitals, no sepsis. Explicitly
    * waking an unconscious player follows the normal state-change event contract. Death respawns
    * instead receive fresh component defaults through the CCA copy strategy.
    */
  def reset(player: ServerPlayer): Unit = {
    val body = Body.get(player)
    BodyPart.values.foreach { part => body.setStats(part, LimbStats()) }

    val vitals = Vitals.get(player)
    vitals.immuneHealth = CasualtiesBelowConfig.MaxImmuneHealth.get()
    vitals.bloodOxygen = VitalsComponent.MaxBloodOxygen
    vitals.bloodVolume = CasualtiesBelowConfig.MaxBloodVolume.get()
    vitals.sepsis = 0.0
    vitals.discomfort = 0.0
    PainShock.resetHealthy(vitals)
    ConsciousnessProgression.resetHealthy(player, vitals)

    Body.sync(player)
    Vitals.sync(player)
  }

  override def registerEntityComponentFactories(
      registry: EntityComponentFactoryRegistry
  ): Unit = {
    registry.registerForPlayers(
      Body,
      (player: Player) => BodyComponentImpl(player),
      RespawnCopyStrategy.LOSSLESS_ONLY
    )
    registry.registerForPlayers(
      Vitals,
      (player: Player) => VitalsComponentImpl(player),
      RespawnCopyStrategy.LOSSLESS_ONLY
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
