package com.rfizzle.respite.gametest.util;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

/**
 * Connected mock-player factory for gametests — the faithful replica of the
 * deprecated {@code GameTestHelper.makeMockServerPlayerInLevel()} built from
 * public, non-deprecated APIs: a real {@link Connection} backed by an
 * {@link EmbeddedChannel} (which absorbs sent packets), fully registered in
 * the player list via {@code placeNewPlayer}. The channel is handed back so
 * tests can read the packets the server sent (e.g. action-bar text).
 */
public final class MockPlayers {

    /** A connected player plus the embedded channel its outbound packets land in. */
    public record Connected(ServerPlayer player, EmbeddedChannel channel) {
    }

    private MockPlayers() {
    }

    /** The connected {@link ServerPlayer} replica; spawns near world spawn — teleport as needed. */
    public static ServerPlayer serverPlayerInLevel(GameTestHelper helper) {
        return connectedServerPlayerInLevel(helper).player();
    }

    /**
     * Fully retires a connected mock: out of the player list, entity
     * discarded — so entries don't accumulate across the shared test server.
     */
    public static void retire(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            server.getPlayerList().remove(player);
        }
        player.discard();
    }

    /**
     * Retires any mock player a previously failed test left in the helper's
     * level, so player-count-sensitive tests start from a clean player list.
     * Call at the top of any test whose assertions depend on who is online.
     */
    public static void retireLeaked(GameTestHelper helper) {
        for (ServerPlayer player : java.util.List.copyOf(helper.getLevel().players())) {
            if ("test-mock-player".equals(player.getGameProfile().getName())) {
                if (player.isSleeping()) {
                    player.stopSleepInBed(true, false);
                }
                retire(player);
            }
        }
    }

    /** Same replica, with the packet-absorbing channel exposed for outbound assertions. */
    public static Connected connectedServerPlayerInLevel(GameTestHelper helper) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-mock-player");
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);

        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };

        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return new Connected(player, channel);
    }
}
