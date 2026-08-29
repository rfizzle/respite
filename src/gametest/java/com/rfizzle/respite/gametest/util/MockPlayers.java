package com.rfizzle.respite.gametest.util;

import com.mojang.authlib.GameProfile;
import com.rfizzle.respite.Respite;
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

    /**
     * The mock profile name, scoped to this mod. A bare {@code "test-mock-player"}
     * is shared by every member's copy of this helper, so {@link #retireLeaked}
     * in one mod would retire another mod's live mock when both are tested into
     * the same level.
     */
    public static final String MOCK_NAME = Respite.MOD_ID + "-test-mock-player";

    private MockPlayers() {
    }

    /** The connected {@link ServerPlayer} replica; spawns near world spawn — teleport as needed. */
    public static ServerPlayer serverPlayerInLevel(GameTestHelper helper) {
        return connectedServerPlayerInLevel(helper).player();
    }

    /**
     * Fully retires a connected mock: woken if asleep, out of the player list,
     * entity discarded — so entries don't accumulate across the shared test
     * server.
     *
     * <p><strong>Idempotent.</strong> {@code PlayerList#remove} calls
     * {@code save(player)} and awards {@code Stats.LEAVE_GAME} with no
     * {@code isRemoved()} guard of its own, so a second call rewrites the
     * player {@code .dat}, the stats JSON, and the advancements JSON, and
     * inflates the leave-game stat. Retiring is the kind of thing a
     * {@code finally} and a leak sweep both reach for, so the guard belongs
     * here rather than re-derived at every call site.
     *
     * <p>The wake is on this path, not only on {@link #retireLeaked}: this is
     * the canonical retirement and takes the bulk of the call sites, and
     * {@code PlayerList#remove} does nothing about sleep on either one.
     */
    public static void retire(ServerPlayer player) {
        if (player.isRemoved()) {
            return;
        }
        wake(player);
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
     *
     * <p>Reads the whole level, so it is only safe in a test that owns its
     * batch alone — same-batch gametests run concurrently in one level, and
     * this would retire a live player out from under a neighbour. Respite
     * declares one batch per test, which is what makes the sweep safe here.
     */
    public static void retireLeaked(GameTestHelper helper) {
        for (ServerPlayer player : java.util.List.copyOf(helper.getLevel().players())) {
            // Matched against the constant, never a bare literal: the name is
            // mod-scoped precisely so this sweep cannot retire a sibling mod's
            // live mock when two members are tested into the same level.
            if (MOCK_NAME.equals(player.getGameProfile().getName())) {
                retire(player);
            }
        }
    }

    /**
     * Wakes a sleeping mock before it leaves the player list.
     *
     * <p>The second argument is the one that matters: it gates
     * {@code ServerLevel.updateSleepingPlayerList()}. Passing {@code false}
     * clears the player's own sleep flag but leaves {@code SleepStatus} still
     * counting someone who is gone, so {@code areEnoughSleeping(...)} reads
     * stale for the rest of the run — and the sleep-vote suite is exactly what
     * reads that state. Vanilla's own {@code stopSleeping()} passes
     * {@code (true, true)}.
     */
    private static void wake(ServerPlayer player) {
        if (player.isSleeping()) {
            player.stopSleepInBed(true, true);
        }
    }

    /** Same replica, with the packet-absorbing channel exposed for outbound assertions. */
    public static Connected connectedServerPlayerInLevel(GameTestHelper helper) {
        return connectedInLevel(helper, false);
    }

    /**
     * A connected replica that reports as a spectator — for tests that assert a
     * spectator is left out of a feature's accounting or its broadcasts.
     */
    public static Connected spectatorServerPlayerInLevel(GameTestHelper helper) {
        return connectedInLevel(helper, true);
    }

    private static Connected connectedInLevel(GameTestHelper helper, boolean spectator) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), MOCK_NAME);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);

        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return spectator;
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
