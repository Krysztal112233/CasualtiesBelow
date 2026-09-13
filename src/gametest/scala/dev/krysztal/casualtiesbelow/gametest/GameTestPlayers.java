package dev.krysztal.casualtiesbelow.gametest;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;

/** Creates uniquely named players with an in-memory connection for damage handling. */
public final class GameTestPlayers {
    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    private GameTestPlayers() {
    }

    public static ServerPlayer createSurvivalPlayer(GameTestHelper helper) {
        return createPlayer(helper, GameType.SURVIVAL);
    }

    public static ServerPlayer createCreativePlayer(GameTestHelper helper) {
        return createPlayer(helper, GameType.CREATIVE);
    }

    private static ServerPlayer createPlayer(GameTestHelper helper, GameType gameType) {
        GameProfile profile = new GameProfile(
                UUID.randomUUID(), "cb-test-" + NEXT_ID.incrementAndGet());
        ClientInformation clientInformation = ClientInformation.createDefault();
        CommonListenerCookie cookie = new CommonListenerCookie(
                profile, 0, clientInformation, false);
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(), helper.getLevel(), profile, clientInformation) {
            @Override
            public GameType gameMode() {
                return gameType;
            }

            @Override
            public boolean isClientAuthoritative() {
                return false;
            }
        };
        gameType.updatePlayerAbilities(player.getAbilities());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        ServerGamePacketListenerImpl listener = new ServerGamePacketListenerImpl(
                helper.getLevel().getServer(), connection, player, cookie);
        for (int tick = 0; tick < 60; tick++) {
            listener.tickClientLoadTimeout();
        }
        return player;
    }
}
