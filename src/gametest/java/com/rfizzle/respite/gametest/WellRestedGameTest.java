package com.rfizzle.respite.gametest;

import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.gametest.util.MockPlayers;
import com.rfizzle.respite.registry.RespiteRegistry;
import com.rfizzle.respite.wellrested.WellRested;
import com.rfizzle.respite.wellrested.WellRestedMath;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * In-world coverage for the Well-Rested grace ({@code design/SPEC.md} §4): the
 * grant on a dawn wake with the configured duration, its two gates (feature off,
 * zero duration) no-op'ing, the ×(1 + bonus) natural-regen boost, and the
 * multiplicative composition with a weariness stage on the rare occasion both are
 * present.
 *
 * <p>Mock players' {@code doTick} isn't driven by the server, so the regen tests
 * drive the awake body tick by hand — the seam the bonus rides — exactly as
 * {@link WearinessGameTest} does for the penalty.
 */
public class WellRestedGameTest implements FabricGameTest {

    private static void guarded(Runnable cleanup, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            cleanup.run();
            throw t;
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wellRestedGrant", timeoutTicks = 100)
    public void dawnWakeGrantsTheGraceForTheConfiguredDuration(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        RespiteConfig config = RespiteConfig.get();
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> MockPlayers.retire(player);
        guarded(cleanup, () -> {
            helper.assertTrue(!player.hasEffect(RespiteRegistry.WELL_RESTED),
                    "a fresh player is not Well-Rested");
            WellRested.grantOnDawnWake(player, config);
            MobEffectInstance instance = player.getEffect(RespiteRegistry.WELL_RESTED);
            helper.assertTrue(instance != null, "a dawn wake grants the Well-Rested effect");
            helper.assertTrue(instance.getDuration() == WellRestedMath.durationTicks(config.wellRestedSeconds),
                    "the grace lasts the configured duration, got " + instance.getDuration());
            cleanup.run();
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wellRestedDisabled", timeoutTicks = 100)
    public void theGrantNoOpsWhenTheFeatureIsOffOrZeroDuration(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        RespiteConfig config = RespiteConfig.get();
        boolean savedEnable = config.enableWellRested;
        int savedSeconds = config.wellRestedSeconds;
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> {
            config.enableWellRested = savedEnable;
            config.wellRestedSeconds = savedSeconds;
            MockPlayers.retire(player);
        };
        guarded(cleanup, () -> {
            // Feature off: no grant.
            config.enableWellRested = false;
            config.wellRestedSeconds = 120;
            WellRested.grantOnDawnWake(player, config);
            helper.assertTrue(!player.hasEffect(RespiteRegistry.WELL_RESTED),
                    "the feature off grants nothing");

            // Enabled but zero duration: no grant (an empty grace is not applied).
            config.enableWellRested = true;
            config.wellRestedSeconds = 0;
            WellRested.grantOnDawnWake(player, config);
            helper.assertTrue(!player.hasEffect(RespiteRegistry.WELL_RESTED),
                    "a zero-second duration grants nothing");

            cleanup.run();
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wellRestedRegen", timeoutTicks = 300)
    public void naturalRegenIsBoostedWhileWellRested(GameTestHelper helper) {
        // Fast regen (food≥20, saturation>0) heals a full 1.0 every 10 ticks; the
        // +50% bonus lifts the first heal to 1.5.
        float expected = (float) (1.0 + RespiteConfig.get().wellRestedRegenBonus);
        runFirstHealTest(helper, expected, false, "Well-Rested heals ×(1 + bonus)");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wellRestedCompose", timeoutTicks = 300)
    public void theBonusComposesWithAWearinessPenalty(GameTestHelper helper) {
        // The rare admin-forced overlap: Exhausted (×0.50) and Well-Rested (×1.50)
        // compose multiplicatively to ×0.75, not one silently dropping the other.
        RespiteConfig config = RespiteConfig.get();
        float expected = (float) ((1.0 - config.exhaustedRegenPenalty) * (1.0 + config.wellRestedRegenBonus));
        runFirstHealTest(helper, expected, true, "Exhausted × Well-Rested composes to ×0.75");
    }

    /**
     * Drive a fresh player primed for fast regen with Well-Rested applied (and,
     * when {@code alsoExhausted}, the Exhausted penalty too), and assert the first
     * natural-regen heal's magnitude equals {@code expected}.
     */
    private void runFirstHealTest(GameTestHelper helper, float expected, boolean alsoExhausted, String label) {
        MockPlayers.retireLeaked(helper);
        ServerPlayer[] current = {null};
        boolean[] setUp = {false};
        int[] budget = {0};
        Runnable cleanup = () -> {
            if (current[0] != null) {
                MockPlayers.retire(current[0]);
            }
        };
        helper.onEachTick(() -> guarded(cleanup, () -> {
            if (!setUp[0]) {
                setUp[0] = true;
                ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
                current[0] = player;
                player.addEffect(new MobEffectInstance(
                        RespiteRegistry.WELL_RESTED, MobEffectInstance.INFINITE_DURATION, 0, true, false, true));
                if (alsoExhausted) {
                    player.addEffect(new MobEffectInstance(
                            RespiteRegistry.EXHAUSTED, MobEffectInstance.INFINITE_DURATION, 0, true, false, true));
                }
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(6.0f);
                player.setHealth(10.0f);
                return;
            }
            ServerPlayer player = current[0];
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(6.0f);
            // The regen mixin reads both feature flags off the global RespiteConfig
            // singleton. Gametest batches all tick on the one server thread, and a
            // concurrent batch (the weariness-disabled test) holds enableWeariness=false
            // across its own tick loop — which would silently drop the Exhausted pole
            // here and heal ×1.5 instead of ×0.75. Re-assert the flags this test needs
            // inside the same synchronous block that drives the heal, then restore them
            // immediately, so the set→heal→restore is atomic and never leaks a value a
            // concurrent test depends on.
            RespiteConfig config = RespiteConfig.get();
            boolean savedWeariness = config.enableWeariness;
            boolean savedWellRested = config.enableWellRested;
            config.enableWeariness = true;
            config.enableWellRested = true;
            // The global weariness sweep reconciles effects off TIME_SINCE_REST
            // every hundred ticks; this mock player's stat reads zero, so a sweep
            // that lands between setup and the heal settles it to stage NONE and
            // strips the admin-forced Exhausted marker — the heal would then
            // compose ×1.5 instead of ×0.75. Re-assert the pole inside the same
            // synchronous set→heal→restore block so no prior-tick sweep can drop
            // it. Well-Rested needs no re-assert: the sweep never touches it.
            if (alsoExhausted && !player.hasEffect(RespiteRegistry.EXHAUSTED)) {
                player.addEffect(new MobEffectInstance(
                        RespiteRegistry.EXHAUSTED, MobEffectInstance.INFINITE_DURATION, 0, true, false, true));
            }
            float before = player.getHealth();
            try {
                player.doTick();
            } finally {
                config.enableWeariness = savedWeariness;
                config.enableWellRested = savedWellRested;
            }
            float healed = player.getHealth() - before;
            if (healed > 0.0f) {
                helper.assertTrue(Math.abs(healed - expected) < 1.0e-4f,
                        label + ": expected " + expected + " healed, got " + healed);
                cleanup.run();
                helper.succeed();
                return;
            }
            if (++budget[0] > 60) {
                helper.fail(label + ": no natural-regen heal fired within 60 ticks");
            }
        }));
    }
}
