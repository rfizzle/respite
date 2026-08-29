package com.rfizzle.respite.gametest;

import com.rfizzle.respite.gametest.util.MockPlayers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

/**
 * Guards the connected-replica's faithfulness so a later "simplification" to a
 * bare {@code new ServerPlayer(...)} fails loudly instead of silently breaking
 * every connection-dependent test.
 */
public class MockPlayersGameTest implements FabricGameTest {

    /** Every assertion here is synchronous — the budget only covers server spin-up. */
    private static final int SYNC = 100;

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "respiteMockPlayerReplica",
            timeoutTicks = SYNC)
    public void connectedReplicaIsFaithful(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            helper.assertTrue(player.connection != null, "replica must have a live connection");
            helper.assertTrue(
                    helper.getLevel().getServer().getPlayerList().getPlayers().contains(player),
                    "replica must be registered in the player list");
            helper.assertTrue(player.level() == helper.getLevel(), "replica must be in the test level");
            helper.assertTrue(player.isCreative(), "replica must report creative");
            helper.assertTrue(!player.isSpectator(), "replica must not report spectator");
        } finally {
            MockPlayers.retire(player);
        }
        helper.succeed();
    }

    /**
     * {@code PlayerList#remove} calls {@code save(player)} and awards
     * {@code Stats.LEAVE_GAME} with no {@code isRemoved()} guard, so an
     * unguarded second retire rewrites the player {@code .dat}, the stats JSON,
     * and the advancements JSON, and double-counts the stat. Retiring is what a
     * {@code finally} and a leak sweep both reach for, so it has to be safe to
     * call twice. The stat is the observable proof: it moves on the first call
     * and must not move on the second.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "respiteMockPlayerRetireIdempotent",
            timeoutTicks = SYNC)
    public void retireIsIdempotent(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);

        MockPlayers.retire(player);
        int afterFirst = player.getStats().getValue(Stats.CUSTOM.get(Stats.LEAVE_GAME));
        helper.assertTrue(player.isRemoved(), "the first retire must remove the player");

        MockPlayers.retire(player);
        helper.assertTrue(player.getStats().getValue(Stats.CUSTOM.get(Stats.LEAVE_GAME)) == afterFirst,
                "a second retire must be a no-op, but it re-ran PlayerList#remove "
                        + "(leave-game stat went " + afterFirst + " -> "
                        + player.getStats().getValue(Stats.CUSTOM.get(Stats.LEAVE_GAME)) + ")");

        helper.succeed();
    }

    /**
     * The wake belongs on {@link MockPlayers#retire}, the canonical path, not
     * only on the leak sweep — {@code PlayerList#remove} does nothing about
     * sleep on either one, so a sleeping mock that is retired directly would
     * leave the level's {@code SleepStatus} counting a player who is gone.
     *
     * <p>The level's {@code SleepStatus} is private with no accessor and respite
     * ships no access widener, so this asserts the player-side half; the
     * {@code updateSleepingPlayerList()} half is pinned by the second argument
     * to {@code stopSleepInBed}, which vanilla's own {@code stopSleeping()}
     * passes as {@code true}.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "respiteMockPlayerRetireWakes",
            timeoutTicks = SYNC)
    public void retireWakesASleepingMock(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        BlockPos bed = player.blockPosition();
        player.startSleeping(bed);
        helper.assertTrue(player.isSleeping(), "precondition: the mock must be asleep");

        MockPlayers.retire(player);

        helper.assertTrue(!player.isSleeping(), "retire must wake a sleeping mock before removing it");
        helper.assertTrue(player.isRemoved(), "retire must still remove the player");
        helper.succeed();
    }

    /**
     * The sweep matches the mod-scoped constant, never a bare literal: five
     * members ship a copy of this helper, and a shared {@code "test-mock-player"}
     * would let one mod's sweep retire another mod's live mock when both are
     * tested into the same level.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "respiteMockPlayerLeakSweep",
            timeoutTicks = SYNC)
    public void retireLeakedClaimsOnlyThisModsMocks(GameTestHelper helper) {
        helper.assertTrue(MockPlayers.MOCK_NAME.startsWith("respite"),
                "the mock profile name must be mod-scoped, was " + MockPlayers.MOCK_NAME);

        ServerPlayer leaked = MockPlayers.serverPlayerInLevel(helper);
        leaked.startSleeping(leaked.blockPosition());

        MockPlayers.retireLeaked(helper);

        helper.assertTrue(leaked.isRemoved(), "the sweep must retire a leaked mock");
        helper.assertTrue(!leaked.isSleeping(), "the sweep must wake it on the way out");
        helper.assertTrue(!helper.getLevel().players().contains(leaked),
                "the sweep must leave the level's player list clean");
        helper.succeed();
    }
}
