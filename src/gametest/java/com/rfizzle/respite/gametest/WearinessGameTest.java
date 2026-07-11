package com.rfizzle.respite.gametest;

import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.gametest.util.MockPlayers;
import com.rfizzle.respite.registry.RespiteRegistry;
import com.rfizzle.respite.weariness.WearinessHandler;
import com.rfizzle.respite.weariness.WearinessMath;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * In-world coverage for {@code design/SPEC.md} §4: the two-stage ladder applied
 * from {@code TIME_SINCE_REST} with the other stage stripped, the stat reset that
 * clears both, the ×0.75 / ×0.50 natural-regen penalty across both vanilla regen
 * branches, the guarantee that direct/potion/beacon healing is never scaled, and
 * the feature toggle that applies nothing and leaves regen untouched.
 *
 * <p>Mock players are registered in the player list but their {@code doTick} is
 * never driven by the server (their connection isn't in the tick loop), so these
 * tests drive the awake body tick by hand — the seam the regen penalty rides —
 * and apply stages through {@link WearinessHandler#sweepPlayer} rather than
 * waiting on the hundred-tick live sweep.
 */
public class WearinessGameTest implements FabricGameTest {

    /** Food/saturation/health that keep the fast-regen branch (food≥20, sat>0, hurt) live. */
    private static void primeForFastRegen(ServerPlayer player) {
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(6.0f);
        player.setHealth(10.0f);
    }

    /** Force the player's rest stat to a fixed tick count so the sweep sees a chosen stage. */
    private static void setTimeSinceRest(ServerPlayer player, long ticks) {
        player.getStats().setValue(player, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), (int) ticks);
    }

    private static long dayTicks(int days) {
        return days * WearinessMath.TICKS_PER_DAY;
    }

    /** Runs the body; on any throwable, cleans up first so later batches stay clean. */
    private static void guarded(Runnable cleanup, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            cleanup.run();
            throw t;
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wearinessLadder", timeoutTicks = 200)
    public void stageLadderAppliesTheMatchingEffectAndStripsTheOther(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        RespiteConfig config = RespiteConfig.get();
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> MockPlayers.retire(player);
        guarded(cleanup, () -> {
            // Below the Weary line: no stage.
            setTimeSinceRest(player, dayTicks(config.wearinessThresholdDays) - 1);
            WearinessHandler.sweepPlayer(player, config);
            assertStage(helper, player, false, false, "one tick under the Weary line is rested");

            // At the Weary line: Weary only.
            setTimeSinceRest(player, dayTicks(config.wearinessThresholdDays));
            WearinessHandler.sweepPlayer(player, config);
            assertStage(helper, player, true, false, "the Weary line applies Weary alone");

            // At the Exhausted line: Exhausted replaces Weary in a single sweep — never both.
            setTimeSinceRest(player, dayTicks(config.exhaustedThresholdDays));
            WearinessHandler.sweepPlayer(player, config);
            assertStage(helper, player, false, true, "the Exhausted line swaps to Exhausted with no both-icons frame");

            cleanup.run();
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wearinessReset", timeoutTicks = 200)
    public void statResetClearsBothStages(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        RespiteConfig config = RespiteConfig.get();
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> MockPlayers.retire(player);
        guarded(cleanup, () -> {
            setTimeSinceRest(player, dayTicks(config.exhaustedThresholdDays));
            WearinessHandler.sweepPlayer(player, config);
            assertStage(helper, player, false, true, "an exhausted stat applies Exhausted");

            // Sleeping/dying/brew all reset TIME_SINCE_REST to zero — the sweep then lifts both.
            setTimeSinceRest(player, 0);
            WearinessHandler.sweepPlayer(player, config);
            assertStage(helper, player, false, false, "a rest reset lifts both stages");

            cleanup.run();
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wearinessRegenFast", timeoutTicks = 300)
    public void naturalRegenScalesTheFastBranchByStage(GameTestHelper helper) {
        // Fast regen (food≥20, saturation>0): heals f/6 = 1.0 every 10 ticks.
        runRegenScalingTest(helper, 20, 6.0f, 40);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wearinessRegenSlow", timeoutTicks = 600)
    public void naturalRegenScalesTheSlowBranchByStage(GameTestHelper helper) {
        // Slow regen (food 18–19, no saturation): heals 1.0 every 80 ticks — the
        // second FoodData#tick heal call site the un-ordinal'd wrap must also catch
        // (SPEC §4.3, both natural-regen branches). Guards against a future MC
        // reorder that would silently leave one call site unscaled.
        runRegenScalingTest(helper, 19, 0.0f, 120);
    }

    /**
     * Drive one fresh player through each stage in turn — rested, Weary, Exhausted —
     * with {@code food}/{@code saturation} chosen to select a specific regen branch,
     * and assert the first natural-regen heal's magnitude scales by that stage's
     * factor. Fresh players per phase so the vanilla regen tick timer starts clean.
     */
    private void runRegenScalingTest(GameTestHelper helper, int food, float saturation, int perPhaseBudget) {
        MockPlayers.retireLeaked(helper);
        RespiteConfig config = RespiteConfig.get();
        float wearyHeal = (float) (1.0 - config.wearinessRegenPenalty);      // 0.75 of a full heal
        float exhaustedHeal = (float) (1.0 - config.exhaustedRegenPenalty);  // 0.50
        float[] expected = {1.0f, wearyHeal, exhaustedHeal};
        long[] statFor = {0, dayTicks(config.wearinessThresholdDays), dayTicks(config.exhaustedThresholdDays)};
        String[] label = {"rested heals a full 1.0", "Weary heals 0.75", "Exhausted heals 0.50"};

        int[] phase = {0};
        ServerPlayer[] current = {null};
        int[] budget = {0};
        Runnable cleanup = () -> {
            if (current[0] != null) {
                MockPlayers.retire(current[0]);
            }
        };
        helper.onEachTick(() -> guarded(cleanup, () -> {
            if (phase[0] >= expected.length) {
                return;
            }
            if (current[0] == null) {
                current[0] = MockPlayers.serverPlayerInLevel(helper);
                if (statFor[phase[0]] > 0) {
                    setTimeSinceRest(current[0], statFor[phase[0]]);
                    WearinessHandler.sweepPlayer(current[0], config);
                }
                current[0].getFoodData().setFoodLevel(food);
                current[0].getFoodData().setSaturation(saturation);
                current[0].setHealth(10.0f);
                budget[0] = 0;
            }
            ServerPlayer player = current[0];
            // Re-assert the branch's inputs (not health) so it stays selected to the heal.
            player.getFoodData().setFoodLevel(food);
            player.getFoodData().setSaturation(saturation);
            float before = player.getHealth();
            player.doTick();
            float healed = player.getHealth() - before;
            if (healed > 0.0f) {
                helper.assertTrue(Math.abs(healed - expected[phase[0]]) < 1.0e-4f,
                        label[phase[0]] + ": expected " + expected[phase[0]] + " healed, got " + healed);
                MockPlayers.retire(player);
                current[0] = null;
                phase[0]++;
                if (phase[0] >= expected.length) {
                    helper.succeed();
                }
                return;
            }
            if (++budget[0] > perPhaseBudget) {
                helper.fail(label[phase[0]] + ": no natural-regen heal fired within " + perPhaseBudget + " ticks");
            }
        }));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wearinessDisableClears", timeoutTicks = 100)
    public void disablingLiftsAnExistingStage(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        RespiteConfig config = RespiteConfig.get();
        boolean savedEnable = config.enableWeariness;
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> {
            config.enableWeariness = savedEnable;
            MockPlayers.retire(player);
        };
        guarded(cleanup, () -> {
            config.enableWeariness = true;
            setTimeSinceRest(player, dayTicks(config.exhaustedThresholdDays));
            WearinessHandler.sweepPlayer(player, config);
            assertStage(helper, player, false, true, "an exhausted stat applies Exhausted while enabled");
            // Feature off must reconcile to none — not leave a stuck indefinite marker
            // (the vanilla-parity-when-off guarantee).
            config.enableWeariness = false;
            WearinessHandler.sweepPlayer(player, config);
            assertStage(helper, player, false, false, "disabling the feature strips the lingering stage");
            cleanup.run();
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wearinessScope", timeoutTicks = 100)
    public void directHealingIsNeverScaled(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        RespiteConfig config = RespiteConfig.get();
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> MockPlayers.retire(player);
        guarded(cleanup, () -> {
            setTimeSinceRest(player, dayTicks(config.exhaustedThresholdDays));
            WearinessHandler.sweepPlayer(player, config);
            helper.assertTrue(player.hasEffect(RespiteRegistry.EXHAUSTED), "player must be Exhausted for the scope check");
            // A direct heal is LivingEntity#heal, not a FoodData#tick regen heal — the
            // wrap is scoped to the food tick, so instant/potion/beacon healing and
            // Restful Saturation's own conversion (all direct heals) are never scaled.
            player.setHealth(10.0f);
            player.heal(4.0f);
            helper.assertTrue(player.getHealth() == 14.0f,
                    "a direct heal must be full even while Exhausted (10 + 4), got " + player.getHealth());
            cleanup.run();
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wearinessDisabled", timeoutTicks = 200)
    public void disabledAppliesNothingAndLeavesRegenUntouched(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        RespiteConfig config = RespiteConfig.get();
        boolean savedEnable = config.enableWeariness;
        config.enableWeariness = false;
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> {
            config.enableWeariness = savedEnable;
            MockPlayers.retire(player);
        };
        boolean[] setUp = {false};
        int[] budget = {0};
        helper.onEachTick(() -> guarded(cleanup, () -> {
            if (!setUp[0]) {
                setUp[0] = true;
                // Even an exhausted stat applies nothing while the feature is off.
                setTimeSinceRest(player, dayTicks(config.exhaustedThresholdDays));
                WearinessHandler.sweepPlayer(player, config);
                assertStage(helper, player, false, false, "the feature off applies neither stage");
                primeForFastRegen(player);
                return;
            }
            // A lingering effect (e.g. from a command) must not scale regen while off.
            // Re-assert it each tick so the disabled reconcile can't strip it before
            // the heal — the point is that the penalty is inert, not that the icon shows.
            player.addEffect(new MobEffectInstance(
                    RespiteRegistry.EXHAUSTED, MobEffectInstance.INFINITE_DURATION, 0, true, false, true));
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(6.0f);
            float before = player.getHealth();
            player.doTick();
            float healed = player.getHealth() - before;
            if (healed > 0.0f) {
                helper.assertTrue(Math.abs(healed - 1.0f) < 1.0e-4f,
                        "with the feature off, regen must be full (1.0) even with the effect present, got " + healed);
                cleanup.run();
                helper.succeed();
                return;
            }
            if (++budget[0] > 40) {
                helper.fail("no natural-regen heal fired within 40 ticks");
            }
        }));
    }

    private static void assertStage(GameTestHelper helper, ServerPlayer player,
                                    boolean weary, boolean exhausted, String why) {
        helper.assertTrue(has(player, RespiteRegistry.WEARY) == weary,
                why + " — Weary expected " + weary);
        helper.assertTrue(has(player, RespiteRegistry.EXHAUSTED) == exhausted,
                why + " — Exhausted expected " + exhausted);
    }

    private static boolean has(ServerPlayer player, Holder<MobEffect> effect) {
        return player.hasEffect(effect);
    }
}
