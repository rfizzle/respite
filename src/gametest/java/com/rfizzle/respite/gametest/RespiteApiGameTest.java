package com.rfizzle.respite.gametest;

import com.rfizzle.respite.api.RespiteAPI;
import com.rfizzle.respite.api.RespiteRestCallback;
import com.rfizzle.respite.chronometer.ChronometerTime;
import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.gametest.util.MockPlayers;
import com.rfizzle.respite.registry.RespiteRegistry;
import com.rfizzle.respite.rest.RestWakeEvents;
import com.rfizzle.respite.weariness.WearinessHandler;
import com.rfizzle.respite.weariness.WearinessMath;
import com.rfizzle.respite.wellrested.WellRested;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.Level;

/**
 * In-world coverage for the public API ({@code design/SPEC.md} §Public API): the
 * read-only accessors reflect live server state, and {@link RespiteRestCallback}
 * fires on a dawn wake with the documented payload. The rate-change callback's
 * one-line primitive edge is exercised by the engine's own tests; here the
 * accessor over it is asserted inactive with no sleepers.
 */
public class RespiteApiGameTest implements FabricGameTest {

    // A single recording listener, registered once — Fabric events don't unregister.
    private static volatile ServerPlayer lastRested;
    private static volatile long lastTicksSlept;
    private static volatile float lastHealthRestored;

    static {
        RespiteRestCallback.EVENT.register((player, ticksSlept, healthRestored) -> {
            lastRested = player;
            lastTicksSlept = ticksSlept;
            lastHealthRestored = healthRestored;
        });
    }

    private static void guarded(Runnable cleanup, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            cleanup.run();
            throw t;
        }
    }

    private static void setTimeSinceRest(ServerPlayer player, long ticks) {
        player.getStats().setValue(player, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), (int) ticks);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "apiAccessors", timeoutTicks = 100)
    public void accessorsReflectLiveServerState(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        RespiteConfig config = RespiteConfig.get();
        ServerLevel overworld = helper.getLevel().getServer().getLevel(Level.OVERWORLD);
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> MockPlayers.retire(player);
        guarded(cleanup, () -> {
            // Chronometer signal mirrors the pure formula for the level's day time.
            int expectedSignal = ChronometerTime.signalFor(
                    overworld.getDayTime(), overworld.dimensionType().hasFixedTime());
            helper.assertTrue(RespiteAPI.getChronometerSignal(overworld) == expectedSignal,
                    "getChronometerSignal should equal the pure formula, expected " + expectedSignal);

            // No sleepers: the lapse is inactive and reads rate 1.
            helper.assertTrue(RespiteAPI.getTimeLapseRate(overworld) == 1,
                    "an idle Overworld reads rate 1, got " + RespiteAPI.getTimeLapseRate(overworld));
            helper.assertTrue(!RespiteAPI.isTimeLapseActive(overworld), "an idle lapse is not active");

            // Rest stat round-trips through the accessor.
            setTimeSinceRest(player, 5_000);
            helper.assertTrue(RespiteAPI.getTicksSinceRest(player) == 5_000L,
                    "getTicksSinceRest should read the stat, got " + RespiteAPI.getTicksSinceRest(player));

            // Weariness accessors track the applied stage, exactly one at a time.
            setTimeSinceRest(player, WearinessMath.TICKS_PER_DAY * config.exhaustedThresholdDays);
            WearinessHandler.sweepPlayer(player, config);
            helper.assertTrue(RespiteAPI.isExhausted(player), "an exhausted stat reads isExhausted");
            helper.assertTrue(!RespiteAPI.isWeary(player), "Exhausted is not also Weary");

            setTimeSinceRest(player, WearinessMath.TICKS_PER_DAY * config.wearinessThresholdDays);
            WearinessHandler.sweepPlayer(player, config);
            helper.assertTrue(RespiteAPI.isWeary(player), "a weary stat reads isWeary");
            helper.assertTrue(!RespiteAPI.isExhausted(player), "Weary is not also Exhausted");

            setTimeSinceRest(player, 0);
            WearinessHandler.sweepPlayer(player, config);
            helper.assertTrue(!RespiteAPI.isWeary(player) && !RespiteAPI.isExhausted(player),
                    "a reset stat clears both accessors");

            // Well-Rested accessor tracks the granted grace, independent of the stat.
            helper.assertTrue(!RespiteAPI.isWellRested(player), "a fresh player is not Well-Rested");
            WellRested.grantOnDawnWake(player, config);
            helper.assertTrue(RespiteAPI.isWellRested(player), "a granted grace reads isWellRested");
            player.removeEffect(RespiteRegistry.WELL_RESTED);
            helper.assertTrue(!RespiteAPI.isWellRested(player), "clearing the effect clears the accessor");

            cleanup.run();
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "apiRestCallback", timeoutTicks = 100)
    public void restCallbackFiresOnDawnWakeWithPayload(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        // Clear the captured reference too, so the static listener never pins a
        // retired player past the test.
        Runnable cleanup = () -> {
            lastRested = null;
            MockPlayers.retire(player);
        };
        guarded(cleanup, () -> {
            lastRested = null;
            lastTicksSlept = -1;
            lastHealthRestored = -1.0f;
            RestWakeEvents.onDawnWake(player, 12_000, 6.0f, true, 4);
            helper.assertTrue(lastRested == player, "the rest callback should fire for the waking player");
            helper.assertTrue(lastTicksSlept == 12_000L,
                    "the callback should carry ticks slept, got " + lastTicksSlept);
            helper.assertTrue(Math.abs(lastHealthRestored - 6.0f) < 1.0e-4f,
                    "the callback should carry health restored, got " + lastHealthRestored);
            cleanup.run();
            helper.succeed();
        });
    }
}
