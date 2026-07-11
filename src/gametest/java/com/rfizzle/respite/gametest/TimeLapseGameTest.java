package com.rfizzle.respite.gametest;

import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.gametest.util.MockPlayers;
import com.rfizzle.respite.timelapse.LapseState;
import com.rfizzle.respite.timelapse.TimeLapseEngine;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * In-world coverage for {@code design/SPEC.md} §1: continuous acceleration
 * with a genuine sleeper, skip suppression at deep sleep, the budget governor,
 * rate recomputation on sleeper removal, the peril brake's clamp and 100-real-
 * tick release, the awake/sleeping body-timer split, the recursion guard, and
 * vanilla parity with the feature off.
 *
 * <p>Two framework facts shape every test here. First, the gametest scheduler
 * measures sequence delays and test timeouts in Overworld <em>game time</em> —
 * which is exactly what the lapse accelerates — so tests drive themselves from
 * {@code onEachTick} (one call per <em>real</em> tick) with their own real-tick
 * counter, cap {@code maxTimeLapseRate} low, and size {@code timeoutTicks} in
 * accelerated game ticks. Second, mock players sleep genuinely (creative
 * bypasses only the nearby-monster check) but their connections are not in the
 * server's connection list, so vanilla never runs their {@code doTick}; tests
 * that need the real-cadence body tick (deep-sleep counters, the vanilla skip)
 * call it explicitly once per real tick, standing in for the connection
 * handler. Every test runs in its own batch and starts by retiring mocks a
 * previously failed test may have leaked.
 */
public class TimeLapseGameTest implements FabricGameTest {

    private static final BlockPos BED_HEAD = new BlockPos(1, 2, 1);
    private static final BlockPos BED_FOOT = new BlockPos(1, 2, 2);
    private static final long NIGHT_START = 13000L;
    /** Low cap so accelerated game time stays within the framework's timeout math. */
    private static final int TEST_RATE_CAP = 4;

    private void placeBed(GameTestHelper helper) {
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        helper.setBlock(BED_FOOT, Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.PART, BedPart.FOOT)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        helper.setBlock(BED_HEAD, Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.PART, BedPart.HEAD)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    /** Teleports the mock beside the bed and puts them genuinely to sleep. */
    private void sleepInBed(GameTestHelper helper, ServerPlayer player) {
        BlockPos head = helper.absolutePos(BED_HEAD);
        player.teleportTo(head.getX() + 0.5, head.getY() + 1, head.getZ() + 1.5);
        var result = player.startSleepInBed(head);
        helper.assertTrue(player.isSleeping(), "mock player must genuinely sleep, got: "
                + result.left().map(Object::toString).orElse("no problem reported"));
    }

    /** Night, a clean player list, a rate cap, and a bed — every test's floor. */
    private int setUpNight(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        helper.getLevel().setDayTime(NIGHT_START);
        placeBed(helper);
        int savedRate = RespiteConfig.get().maxTimeLapseRate;
        RespiteConfig.get().maxTimeLapseRate = TEST_RATE_CAP;
        return savedRate;
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

    /**
     * The framework invokes {@code onEachTick} listeners once per <em>game</em>
     * tick — it catches its game-time counter up past the lapse's extra ticks —
     * so real-tick-driven tests dedupe on the engine's real-tick clock: true
     * exactly once per real server tick.
     */
    private static boolean newRealTick(long[] lastSeen) {
        long realTick = TimeLapseEngine.getRealTickCount();
        if (realTick == lastSeen[0]) {
            return false;
        }
        lastSeen[0] = realTick;
        return true;
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "timeLapseAccelerates", timeoutTicks = 300)
    public void sleeperAcceleratesTheNightContinuously(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        int savedRate = setUpNight(helper);
        ServerPlayer sleeper = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> {
            RespiteConfig.get().maxTimeLapseRate = savedRate;
            MockPlayers.retire(sleeper);
        };
        int[] realTick = {0};
        int[] samples = {0};
        long[] lastDayTime = {0};
        long[] lastRealTick = {-1};
        long[] baselineEvaluations = {0};
        helper.onEachTick(() -> guarded(cleanup, () -> {
            if (!newRealTick(lastRealTick)) {
                return;
            }
            int t = ++realTick[0];
            if (t == 2) {
                sleepInBed(helper, sleeper);
            } else if (t == 4) {
                // two warm-up ticks after sleep: the engine and skyDarken settled
                lastDayTime[0] = overworld.getDayTime();
                baselineEvaluations[0] = TimeLapseEngine.getRateEvaluations();
            } else if (t > 4 && samples[0] < 5) {
                long now = overworld.getDayTime();
                long delta = now - lastDayTime[0];
                lastDayTime[0] = now;
                samples[0]++;
                helper.assertTrue(sleeper.isSleeping(), "the sleeper must stay in bed");
                helper.assertTrue(delta > 1, "dayTime must advance more than 1 per real tick, got " + delta);
                helper.assertTrue(delta <= TEST_RATE_CAP,
                        "dayTime advanced " + delta + " in one real tick — discontinuous jump");
                helper.assertTrue(delta == TimeLapseEngine.getEffectiveRate(),
                        "dayTime delta " + delta + " must equal the reported effective rate "
                                + TimeLapseEngine.getEffectiveRate());
                if (samples[0] == 5) {
                    // recursion guard: 5 real ticks sampled → exactly 5 evaluations,
                    // no matter how many extra world ticks ran inside them
                    long evaluations = TimeLapseEngine.getRateEvaluations() - baselineEvaluations[0];
                    helper.assertTrue(evaluations == 5, "rate evaluated " + evaluations
                            + " times over 5 real ticks — extra ticks re-entered the accelerator");
                    helper.assertTrue(!TimeLapseEngine.isExtraTickInProgress(),
                            "the extra-tick flag must be clear on the real-tick path");
                    cleanup.run();
                    helper.succeed();
                }
            } else if (t >= 80) {
                helper.fail("sampling never completed after 80 real ticks");
            }
        }));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "timeLapseSuppression", timeoutTicks = 900)
    public void vanillaSkipStaysSuppressedThroughDeepSleep(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        int savedRate = setUpNight(helper);
        ServerPlayer sleeper = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> {
            RespiteConfig.get().maxTimeLapseRate = savedRate;
            MockPlayers.retire(sleeper);
        };
        int[] realTick = {0};
        long[] lastDayTime = {-1};
        long[] lastRealTick = {-1};
        helper.onEachTick(() -> guarded(cleanup, () -> {
            if (!newRealTick(lastRealTick)) {
                return;
            }
            int t = ++realTick[0];
            if (t == 2) {
                sleepInBed(helper, sleeper);
                return;
            }
            if (t < 2) {
                return;
            }
            if (sleeper.isSleeping()) {
                sleeper.doTick(); // the real-cadence body tick vanilla would run — marches sleepCounter to deep sleep
            }
            long now = overworld.getDayTime();
            if (lastDayTime[0] >= 0) {
                long delta = now - lastDayTime[0];
                helper.assertTrue(delta <= TEST_RATE_CAP,
                        "dayTime jumped " + delta + " in one real tick — the vanilla skip fired");
            }
            lastDayTime[0] = now;
            if (t == 140) {
                // deep sleep (100 ticks in bed) long since reached, the one-sleeper
                // playersSleepingPercentage bar long since met — and yet:
                helper.assertTrue(sleeper.isSleeping(),
                        "the sleeper must still be in bed — vanilla would have woken everyone");
                helper.assertTrue(now < 23000L, "still night — a skip would have landed at morning");
                cleanup.run();
                helper.succeed();
            } else if (t > 160) {
                helper.fail("the 140-real-tick checkpoint never fired");
            }
        }));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "timeLapseGovernor", timeoutTicks = 300)
    public void tinyBudgetStarvesTheRateAndSleeperRemovalSettles(GameTestHelper helper) {
        int savedRate = setUpNight(helper);
        ServerPlayer sleeper = MockPlayers.serverPlayerInLevel(helper);
        int savedBudget = RespiteConfig.get().timeLapseTickBudgetMs;
        RespiteConfig.get().timeLapseTickBudgetMs = 0; // below the loadable range — a deliberately impossible budget
        Runnable cleanup = () -> {
            RespiteConfig.get().maxTimeLapseRate = savedRate;
            RespiteConfig.get().timeLapseTickBudgetMs = savedBudget;
            MockPlayers.retire(sleeper);
        };
        int[] realTick = {0};
        long[] lastRealTick = {-1};
        helper.onEachTick(() -> guarded(cleanup, () -> {
            if (!newRealTick(lastRealTick)) {
                return;
            }
            int t = ++realTick[0];
            switch (t) {
                case 2 -> sleepInBed(helper, sleeper);
                case 6 -> {
                    helper.assertTrue(TimeLapseEngine.getSleeping() == 1,
                            "the engine must count exactly the one sleeper, got "
                                    + TimeLapseEngine.getSleeping());
                    helper.assertTrue(TimeLapseEngine.getEffectiveRate() == 1,
                            "a spent budget must starve the effective rate below the target, got "
                                    + TimeLapseEngine.getEffectiveRate());
                    RespiteConfig.get().timeLapseTickBudgetMs = savedBudget;
                }
                case 10 -> {
                    helper.assertTrue(TimeLapseEngine.getEffectiveRate() > 1,
                            "a restored budget must let the rate climb");
                    sleeper.stopSleepInBed(true, true);
                }
                case 13 -> {
                    helper.assertTrue(TimeLapseEngine.getEffectiveRate() == 1,
                            "removing the sleeper must settle the rate to 1, got "
                                    + TimeLapseEngine.getEffectiveRate());
                    helper.assertTrue(TimeLapseEngine.getSleeping() == 0, "no sleepers remain");
                    cleanup.run();
                    helper.succeed();
                }
                default -> {
                    if (t >= 60) {
                        helper.fail("the phase machine stalled before tick 13 completed");
                    }
                }
            }
        }));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "timeLapsePeril", timeoutTicks = 600)
    public void perilBrakeClampsAndReleasesAfterTheWindow(GameTestHelper helper) {
        int savedRate = setUpNight(helper);
        ServerPlayer sleeper = MockPlayers.serverPlayerInLevel(helper);
        ServerPlayer awake = MockPlayers.serverPlayerInLevel(helper);
        BlockPos awakePos = helper.absolutePos(new BlockPos(2, 2, 2));
        awake.teleportTo(awakePos.getX() + 0.5, awakePos.getY(), awakePos.getZ() + 0.5);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 2, 0));
        zombie.setNoAi(true); // the mixin fires on setTarget; AI must not retarget on its own
        Runnable cleanup = () -> {
            RespiteConfig.get().maxTimeLapseRate = savedRate;
            MockPlayers.retire(sleeper);
            MockPlayers.retire(awake);
        };
        int[] realTick = {0};
        long[] lastRealTick = {-1};
        helper.onEachTick(() -> guarded(cleanup, () -> {
            if (!newRealTick(lastRealTick)) {
                return;
            }
            int t = ++realTick[0];
            switch (t) {
                case 2 -> sleepInBed(helper, sleeper);
                case 6 -> {
                    helper.assertTrue(TimeLapseEngine.getEffectiveRate() > 1,
                            "the lapse must be running before the fight starts");
                    zombie.setTarget(awake);
                }
                case 10 -> {
                    helper.assertTrue(TimeLapseEngine.getEffectiveRate() == 1,
                            "a hostile targeting an awake player must clamp the rate to 1");
                    helper.assertTrue(TimeLapseEngine.getState() == LapseState.HELD,
                            "the engine must report the hold, got " + TimeLapseEngine.getState());
                    zombie.setTarget(null);
                }
                // the window refreshed through tick 10's evaluation; release lands
                // ~100 real ticks later — still held at 95, running again by 125
                case 105 -> helper.assertTrue(TimeLapseEngine.getEffectiveRate() == 1,
                        "the peril window must hold for 100 real ticks after the fight ends");
                case 135 -> {
                    helper.assertTrue(TimeLapseEngine.getEffectiveRate() > 1,
                            "the lapse must resume once the peril window decays, got rate "
                                    + TimeLapseEngine.getEffectiveRate() + " state "
                                    + TimeLapseEngine.getState());
                    cleanup.run();
                    helper.succeed();
                }
                default -> {
                    if (t >= 250) {
                        helper.fail("the phase machine stalled before tick 135 completed");
                    }
                }
            }
        }));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "timeLapseTimers", timeoutTicks = 400)
    public void awakeBodyTimersHoldWhileSleepersAdvance(GameTestHelper helper) {
        int savedRate = setUpNight(helper);
        ServerPlayer sleeper = MockPlayers.serverPlayerInLevel(helper);
        ServerPlayer awake = MockPlayers.serverPlayerInLevel(helper);
        BlockPos awakePos = helper.absolutePos(new BlockPos(2, 2, 2));
        awake.teleportTo(awakePos.getX() + 0.5, awakePos.getY(), awakePos.getZ() + 0.5);
        Runnable cleanup = () -> {
            RespiteConfig.get().maxTimeLapseRate = savedRate;
            MockPlayers.retire(sleeper);
            MockPlayers.retire(awake);
        };
        int[] realTick = {0};
        long[] lastRealTick = {-1};
        int[] baseline = new int[2];
        int[] extrasSum = {0};
        float[] saturationBaseline = new float[2];
        helper.onEachTick(() -> guarded(cleanup, () -> {
            if (!newRealTick(lastRealTick)) {
                return;
            }
            int t = ++realTick[0];
            if (t == 2) {
                sleepInBed(helper, sleeper);
                for (ServerPlayer player : new ServerPlayer[] {sleeper, awake}) {
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20000));
                    // straight into FoodData: causeFoodExhaustion is a no-op for the
                    // creative-ability mock, and the body tick drains regardless
                    player.getFoodData().addExhaustion(20.0f);
                }
                baseline[0] = sleeper.getEffect(MobEffects.MOVEMENT_SPEED).getDuration();
                baseline[1] = awake.getEffect(MobEffects.MOVEMENT_SPEED).getDuration();
                saturationBaseline[0] = sleeper.getFoodData().getSaturationLevel();
                saturationBaseline[1] = awake.getFoodData().getSaturationLevel();
            } else if (t > 2 && t < 32) {
                // the CPU-saturated test server may budget-throttle the rate well
                // below the cap, so expectations track the extras actually run
                extrasSum[0] += TimeLapseEngine.getEffectiveRate() - 1;
            } else if (t == 32) {
                helper.assertTrue(TimeLapseEngine.getEffectiveRate() > 1, "the lapse must be running");
                int sleeperSpent = baseline[0] - sleeper.getEffect(MobEffects.MOVEMENT_SPEED).getDuration();
                int awakeSpent = baseline[1] - awake.getEffect(MobEffects.MOVEMENT_SPEED).getDuration();
                helper.assertTrue(extrasSum[0] > 10,
                        "the lapse must have run a meaningful number of extra ticks, got " + extrasSum[0]);
                // each extra tick body-ticks the sleeper exactly once; ±5 absorbs
                // the sampling offset at the window edges
                helper.assertTrue(Math.abs(sleeperSpent - extrasSum[0]) <= 5,
                        "a sleeper's effect timers must ride the extra ticks: spent " + sleeperSpent
                                + " against " + extrasSum[0] + " extra ticks run");
                // the awake body tick runs at most once per real tick (for mock
                // players vanilla never runs it at all — the bound still proves
                // extra ticks contributed nothing)
                helper.assertTrue(awakeSpent <= 35,
                        "an awake player's effect timers must hold to real cadence; spent " + awakeSpent);
                helper.assertTrue(sleeper.getFoodData().getSaturationLevel() < saturationBaseline[0],
                        "a sleeper's primed hunger drain must advance during extra ticks");
                helper.assertTrue(awake.getFoodData().getSaturationLevel() == saturationBaseline[1],
                        "an awake player's hunger must not move on extra ticks");
                cleanup.run();
                helper.succeed();
            } else if (t >= 80) {
                helper.fail("the tick-32 checkpoint never fired");
            }
        }));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "timeLapseDisabled", timeoutTicks = 300)
    public void disabledConfigRestoresTheVanillaSkip(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        MockPlayers.retireLeaked(helper);
        overworld.setDayTime(NIGHT_START);
        long nextMorning = (NIGHT_START / 24000L + 1) * 24000L;
        placeBed(helper);
        ServerPlayer sleeper = MockPlayers.serverPlayerInLevel(helper);
        boolean saved = RespiteConfig.get().enableTimeLapse;
        RespiteConfig.get().enableTimeLapse = false;
        Runnable cleanup = () -> {
            RespiteConfig.get().enableTimeLapse = saved;
            MockPlayers.retire(sleeper);
        };
        int[] realTick = {0};
        long[] lastRealTick = {-1};
        helper.onEachTick(() -> guarded(cleanup, () -> {
            if (!newRealTick(lastRealTick)) {
                return;
            }
            int t = ++realTick[0];
            if (t == 2) {
                sleepInBed(helper, sleeper);
                return;
            }
            if (sleeper.isSleeping()) {
                sleeper.doTick(); // marches the vanilla deep-sleep counter at real cadence
            }
            // deep sleep lands at 100 ticks in bed; vanilla then jumps to morning
            // and wakes everyone — reaching morning any other way would need
            // ~11,000 real ticks, far past this test's timeout
            if (overworld.getDayTime() >= nextMorning) {
                helper.assertTrue(!sleeper.isSleeping(), "vanilla must wake the sleeper at the skip");
                helper.assertTrue(TimeLapseEngine.getEffectiveRate() == 1,
                        "the engine must stay settled while disabled");
                cleanup.run();
                helper.succeed();
            } else if (t >= 250) {
                helper.fail("vanilla skip never fired with the feature off; dayTime is "
                        + overworld.getDayTime());
            }
        }));
    }
}
