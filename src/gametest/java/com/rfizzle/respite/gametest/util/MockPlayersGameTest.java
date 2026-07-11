package com.rfizzle.respite.gametest.util;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * Guards the connected-replica's faithfulness so a later "simplification" to a
 * bare {@code new ServerPlayer(...)} fails loudly instead of silently breaking
 * every connection-dependent test.
 */
public class MockPlayersGameTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
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
}
