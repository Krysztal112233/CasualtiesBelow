package dev.krysztal.casualtiesbelow

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory
import net.minecraft.resources.Identifier

object CasualtiesBelow extends ModInitializer {
  val Logger = LoggerFactory.getLogger("casualtiesbelow")

  override def onInitialize(): Unit = {
    Logger.info("CasualtiesBelow initialized")
  }

  def ofIdentifier(path: String): Identifier =
    Identifier.fromNamespaceAndPath("casualtiesbelow", path)

}
