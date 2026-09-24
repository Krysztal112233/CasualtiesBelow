package dev.krysztal.casualtiesbelow.internal.extension

import scala.jdk.CollectionConverters.*

import net.minecraft.server.MinecraftServer
private[casualtiesbelow] object MinecraftServerExtensions {

  extension (server: MinecraftServer) {
    def getPlayers = server.getPlayerList().getPlayers().asScala.toList
  }
}
