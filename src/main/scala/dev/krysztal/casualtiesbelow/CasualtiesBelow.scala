package dev.krysztal.casualtiesbelow

import net.minecraft.resources.Identifier

import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.damage.LimbDamage

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object CasualtiesBelow extends ModInitializer {
  val ModId = "casualtiesbelow"
  val Logger = LoggerFactory.getLogger(ModId)

  override def onInitialize(): Unit = {
    CasualtiesBelowConfig.register()
    CasualtiesBelowCommands.register()
    LimbDamage.register()
    Logger.info("CasualtiesBelow initialized")
  }

  def ofIdentifier(path: String): Identifier =
    Identifier.fromNamespaceAndPath(ModId, path)

}
