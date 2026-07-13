package com.rfizzle.respite.gametest;

import com.rfizzle.respite.Respite;
import com.rfizzle.respite.chronometer.PocketChronometerItem;
import com.rfizzle.respite.command.StatusFormat;
import com.rfizzle.respite.gametest.util.MockPlayers;
import com.rfizzle.respite.registry.RespiteRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;

/**
 * In-world coverage for the pocket chronometer ({@code design/SPEC.md} §5): the
 * recipe and its unlock advancement load under the default config, and the
 * server-side refresh carries the holder's {@code TIME_SINCE_REST} onto the stack
 * (the client-only tooltip's sole server-sourced fact), rewriting only when the
 * displayed days figure moves.
 *
 * <p>Mock players are not driven by the server tick loop, so the refresh is
 * exercised through {@link PocketChronometerItem#refreshAwakeTicks} directly.
 */
public class PocketChronometerGameTest implements FabricGameTest {

    private static void setTimeSinceRest(ServerPlayer player, long ticks) {
        player.getStats().setValue(player, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), (int) ticks);
    }

    private static int stored(ItemStack stack) {
        return stack.getOrDefault(RespiteRegistry.AWAKE_TICKS, 0);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "pocketChronometerRecipe", timeoutTicks = 100)
    public void recipeAndUnlockAdvancementShipUnderDefaultConfig(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.getRecipeManager().byKey(Respite.id("pocket_chronometer")).isPresent(),
                "the pocket chronometer recipe must load under the default (enabled) config");
        helper.assertTrue(server.getAdvancements().get(Respite.id("recipes/misc/pocket_chronometer")) != null,
                "the recipe-unlock advancement must load under the default config");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "pocketChronometerCarry", timeoutTicks = 200)
    public void refreshCarriesDaysAwakeAndHoldsWithinATenth(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> MockPlayers.retire(player);
        try {
            ItemStack stack = new ItemStack(RespiteRegistry.POCKET_CHRONOMETER);

            // 3.5 days awake — the refresh carries the exact stat onto the stack.
            setTimeSinceRest(player, 84_000L);
            PocketChronometerItem.refreshAwakeTicks(stack, player);
            helper.assertTrue(stored(stack) == 84_000,
                    "the stack must carry the holder's rest ticks, got " + stored(stack));
            helper.assertTrue(StatusFormat.awakeDays(stored(stack)).equals("3.5"),
                    "the carried figure must read like /respite status");

            // A sub-tenth increment leaves the carried value untouched (no per-tick resync).
            setTimeSinceRest(player, 84_100L);
            PocketChronometerItem.refreshAwakeTicks(stack, player);
            helper.assertTrue(stored(stack) == 84_000,
                    "a sub-tenth change must not rewrite the stack, got " + stored(stack));

            // Crossing to 3.6 days rewrites.
            setTimeSinceRest(player, 86_400L);
            PocketChronometerItem.refreshAwakeTicks(stack, player);
            helper.assertTrue(stored(stack) == 86_400,
                    "crossing a displayed tenth must rewrite the stack, got " + stored(stack));

            cleanup.run();
            helper.succeed();
        } catch (Throwable t) {
            cleanup.run();
            throw t;
        }
    }
}
