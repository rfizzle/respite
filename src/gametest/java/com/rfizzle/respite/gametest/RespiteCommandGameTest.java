package com.rfizzle.respite.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.gametest.util.MockPlayers;
import com.rfizzle.respite.registry.RespiteRegistry;
import com.rfizzle.respite.weariness.WearinessMath;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

/**
 * In-world coverage for {@code /respite} ({@code design/SPEC.md} §Commands): the
 * tree registers with per-node permission gating (status open, reload/rest
 * op-only), and the rest levers write the stat and drive the Weariness ladder
 * within the command rather than at the next sweep.
 */
public class RespiteCommandGameTest implements FabricGameTest {

    private static void guarded(Runnable cleanup, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            cleanup.run();
            throw t;
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "commandTree", timeoutTicks = 100)
    public void treeRegistersWithPerNodePermissionGating(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CommandNode<CommandSourceStack> root = server.getCommands().getDispatcher().getRoot().getChild("respite");
        helper.assertTrue(root != null, "the /respite root should be registered");

        CommandSourceStack nonOp = server.createCommandSourceStack().withPermission(0);
        CommandSourceStack op = server.createCommandSourceStack().withPermission(2);

        helper.assertTrue(root.getChild("status").canUse(nonOp), "status should be open to everyone");
        helper.assertTrue(!root.getChild("reload").canUse(nonOp), "reload should deny non-ops");
        helper.assertTrue(root.getChild("reload").canUse(op), "reload should allow ops");
        helper.assertTrue(!root.getChild("rest").canUse(nonOp), "rest should deny non-ops");
        helper.assertTrue(root.getChild("rest").canUse(op), "rest should allow ops");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "commandStatus", timeoutTicks = 100)
    public void statusExecutesTheReadForAnyPlayer(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> MockPlayers.retire(player);
        guarded(cleanup, () -> {
            // status is perm-0 and self-scoped: run it from the player's own source
            // and assert the body ran to its success return, driving the lapse,
            // awake-stage, moon, and looked-at-Chronometer reads end to end.
            int result;
            try {
                result = server.getCommands().getDispatcher()
                        .execute("respite status", player.createCommandSourceStack());
            } catch (CommandSyntaxException e) {
                throw new AssertionError("respite status should parse and execute", e);
            }
            helper.assertTrue(result == 1, "respite status should return success, got " + result);
            cleanup.run();
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "commandReload", timeoutTicks = 200)
    public void reloadRunsTheConfigAndResourceReload(GameTestHelper helper) {
        // reload rereads the config from disk, diffs it, and re-fires the vanilla
        // resource reload (the config-bound recipe/advancement gates). Run the whole
        // op-gated body and assert it returns success — the async resource reload is
        // fired but not awaited, matching /reload.
        MinecraftServer server = helper.getLevel().getServer();
        CommandSourceStack op = server.createCommandSourceStack().withPermission(2);
        int result;
        try {
            result = server.getCommands().getDispatcher().execute("respite reload", op);
        } catch (CommandSyntaxException e) {
            throw new AssertionError("respite reload should parse and execute", e);
        }
        helper.assertTrue(result == 1, "respite reload should reload and return success, got " + result);
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "commandRest", timeoutTicks = 100)
    public void restSetAndClearDriveTheStatAndLadder(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        MinecraftServer server = helper.getLevel().getServer();
        RespiteConfig config = RespiteConfig.get();
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        CommandSourceStack source = player.createCommandSourceStack().withPermission(2);
        Runnable cleanup = () -> MockPlayers.retire(player);
        guarded(cleanup, () -> {
            // set <days> writes the stat and the ladder reacts now — a day count past
            // the Weary line (but short of Exhausted) lands exactly Weary.
            int days = config.wearinessThresholdDays + 1;
            server.getCommands().performPrefixedCommand(source, "respite rest set " + days);
            int expected = days * (int) WearinessMath.TICKS_PER_DAY;
            helper.assertTrue(player.getStats().getValue(Stats.CUSTOM, Stats.TIME_SINCE_REST) == expected,
                    "rest set should write days×24000, got "
                            + player.getStats().getValue(Stats.CUSTOM, Stats.TIME_SINCE_REST));
            helper.assertTrue(player.hasEffect(RespiteRegistry.WEARY),
                    days + " days awake should apply Weary at once");
            helper.assertTrue(!player.hasEffect(RespiteRegistry.EXHAUSTED),
                    "short of the Exhausted line, only Weary applies");

            // clear resets the stat and lifts the stage.
            server.getCommands().performPrefixedCommand(source, "respite rest clear");
            helper.assertTrue(player.getStats().getValue(Stats.CUSTOM, Stats.TIME_SINCE_REST) == 0,
                    "rest clear should zero the stat");
            helper.assertTrue(!player.hasEffect(RespiteRegistry.WEARY) && !player.hasEffect(RespiteRegistry.EXHAUSTED),
                    "rest clear should lift both stages");
            cleanup.run();
            helper.succeed();
        });
    }
}
