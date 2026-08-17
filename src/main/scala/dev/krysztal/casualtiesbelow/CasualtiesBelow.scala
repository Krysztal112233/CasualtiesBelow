package dev.krysztal.casualtiesbelow

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object CasualtiesBelow extends ModInitializer {
  val Logger = LoggerFactory.getLogger("casualtiesbelow")

  override def onInitialize(): Unit = {
    Logger.info("CasualtiesBelow initialized")
  }
}
