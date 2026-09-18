package dev.krysztal.casualtiesbelow.gametest

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

import com.mojang.authlib.GameProfile
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.Connection
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.level.GameType

import io.netty.channel.embedded.EmbeddedChannel

/** Creates uniquely named players with an in-memory connection for damage handling. */
object GameTestPlayers {
  private val NextId = new AtomicInteger()

  def createSurvivalPlayer(helper: GameTestHelper): ServerPlayer = {
    createPlayer(helper, GameType.SURVIVAL)
  }

  def createCreativePlayer(helper: GameTestHelper): ServerPlayer = {
    createPlayer(helper, GameType.CREATIVE)
  }

  private def createPlayer(helper: GameTestHelper, gameType: GameType): ServerPlayer = {
    val profile = new GameProfile(UUID.randomUUID(), s"cb-test-${NextId.incrementAndGet()}")
    val clientInformation = ClientInformation.createDefault()
    val cookie = new CommonListenerCookie(profile, 0, clientInformation, false)
    val player = new ServerPlayer(
      helper.getLevel.getServer,
      helper.getLevel,
      profile,
      clientInformation
    ) {
      override def gameMode(): GameType = gameType
      override def isClientAuthoritative: Boolean = false
    }
    gameType.updatePlayerAbilities(player.getAbilities)
    val connection = new Connection(PacketFlow.SERVERBOUND)
    // Attaches the channel as a side effect so the packet listener below can pump packets.
    val _ = new EmbeddedChannel(connection)
    val listener =
      new ServerGamePacketListenerImpl(helper.getLevel.getServer, connection, player, cookie)
    (0 until 60).foreach(_ => listener.tickClientLoadTimeout())
    player
  }
}
