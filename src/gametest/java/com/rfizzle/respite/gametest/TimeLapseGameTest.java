package com.rfizzle.respite.gametest;

import com.rfizzle.respite.api.RespiteTimeLapseCallback;
import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.gametest.util.MockPlayers;
import com.rfizzle.respite.timelapse.LapseState;
import com.rfizzle.respite.timelapse.TimeLapseEngine;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
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
 * tick release, the awake/sleeping body-timer split, the recursion guard, the
 * notifier's per-edge send loop plus its {@code announceTimeLapse} gate, the
 * public {@link RespiteTimeLapseCallback} rate-edge fire, and vanilla parity
 * with the feature off.
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

    // A single recording listener on the public rate-change callback, registered
    // once — Fabric events don't unregister. Only the two notifier tests arm it,
    // and each resets its counters first, so a lapse from another batch never
    // pollutes what these assertions read.
    private static volatile boolean callbackArmed = false;
    private static volatile int callbackOldRate = -1;
    private static volatile int callbackNewRate = -1;
    private static volatile int startEdges = 0;
    private static volatile int endEdges = 0;

    static {
        RespiteTimeLapseCallback.EVENT.register((level, oldRate, newRate, sleeping, total) -> {
            if (!callbackArmed) {
                return;
            }
            callbackOldRate = oldRate;
            callbackNewRate = newRate;
            if (oldRate == 1 && newRate > 1) {
                startEdges++;
            }
            if (newRate == 1 && oldRate > 1) {
                endEdges++;
            }
        });
    }

    private static void armCallback() {
        startEdges = 0;
        endEdges = 0;
        callbackOldRate = -1;
        callbackNewRate = -1;
        callbackArmed = true;
    }

    /**
     * The action-bar (overlay) translation keys currently in the mock's channel —
     * the vanilla-client fallback the notifier sends to a connectionless mock,
     * which {@code canSend} always reports unreachable by the mod's payload. The
     * server batch-flushes connections mid-tick, so the channel is flushed here
     * before reading.
     */
    private static List<String> overlayKeys(MockPlayers.Connected connected) {
        connected.channel().flush();
        List<String> keys = new ArrayList<>();
        for (Object message : connected.channel().outboundMessages()) {
            if (message instanceof ClientboundSystemChatPacket packet && packet.overlay()
                    && packet.content().getContents() instanceof TranslatableContents contents) {
                keys.add(contents.getKey());
            }
        }
        return keys;
    }

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
        // Isolate §1's body-timer split: Restful Saturation (§2) freezes a
        // sleeper's food tick in bed, which would mask the hunger-drain signal
        // this test uses to prove sleepers ride the extra ticks.
        boolean savedRestful = RespiteConfig.get().enableRestfulSaturation;
        RespiteConfig.get().enableRestfulSaturation = false;
        ServerPlayer sleeper = MockPlayers.serverPlayerInLevel(helper);
        ServerPlayer awake = MockPlayers.serverPlayerInLevel(helper);
        BlockPos awakePos = helper.absolutePos(new BlockPos(2, 2, 2));
        awake.teleportTo(awakePos.getX() + 0.5, awakePos.getY(), awakePos.getZ() + 0.5);
        Runnable cleanup = () -> {
            RespiteConfig.get().maxTimeLapseRate = savedRate;
            RespiteConfig.get().enableRestfulSaturation = savedRestful;
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

    /**
     * The notifier's send loop and the public callback, driven by a genuine
     * lapse with {@code announceTimeLapse} on: the start edge fires the callback
     * (old rate 1 → running) and pushes the active line to the connectionless
     * mock's fallback channel; waking the sleeper settles the rate, firing the
     * end edge (→ 1) and the settle line.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "timeLapseNotifier", timeoutTicks = 400)
    public void notifierAndCallbackReportRateEdges(GameTestHelper helper) {
        int savedRate = setUpNight(helper);
        boolean savedAnnounce = RespiteConfig.get().announceTimeLapse;
        RespiteConfig.get().announceTimeLapse = true;
        MockPlayers.Connected connected = MockPlayers.connectedServerPlayerInLevel(helper);
        ServerPlayer sleeper = connected.player();
        armCallback();
        Runnable cleanup = () -> {
            callbackArmed = false;
            RespiteConfig.get().maxTimeLapseRate = savedRate;
            RespiteConfig.get().announceTimeLapse = savedAnnounce;
            MockPlayers.retire(sleeper);
        };
        int[] realTick = {0};
        long[] lastRealTick = {-1};
        boolean[] woke = {false};
        Set<String> activeKeys = new HashSet<>();
        Set<String> settledKeys = new HashSet<>();
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
            if (!woke[0]) {
                activeKeys.addAll(overlayKeys(connected));
                if (TimeLapseEngine.getEffectiveRate() > 1 && startEdges >= 1
                        && activeKeys.contains("notification.respite.time_lapse")) {
                    helper.assertTrue(callbackOldRate == 1 && callbackNewRate > 1,
                            "the start edge must report old rate 1 to a running rate, got "
                                    + callbackOldRate + " -> " + callbackNewRate);
                    connected.channel().outboundMessages().clear();
                    sleeper.stopSleepInBed(true, true);
                    woke[0] = true;
                } else if (t >= 120) {
                    helper.fail("no start edge announced within 120 real ticks; keys=" + activeKeys);
                }
            } else {
                settledKeys.addAll(overlayKeys(connected));
                if (TimeLapseEngine.getEffectiveRate() == 1 && endEdges >= 1
                        && settledKeys.contains("notification.respite.time_lapse_end")) {
                    helper.assertTrue(callbackNewRate == 1,
                            "the end edge must report a settle to rate 1, got " + callbackNewRate);
                    cleanup.run();
                    helper.succeed();
                } else if (t >= 240) {
                    helper.fail("no settle edge announced after the sleeper woke; keys=" + settledKeys);
                }
            }
        }));
    }

    /**
     * The {@code announceTimeLapse} gate is server-side and player-facing only:
     * with it off, a genuine lapse fires the public callback exactly as before
     * but sends no action-bar line to any client.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "timeLapseNotifierGate", timeoutTicks = 400)
    public void announceToggleOffKeepsTheCallbackButSuppressesTheLine(GameTestHelper helper) {
        int savedRate = setUpNight(helper);
        boolean savedAnnounce = RespiteConfig.get().announceTimeLapse;
        RespiteConfig.get().announceTimeLapse = false;
        MockPlayers.Connected connected = MockPlayers.connectedServerPlayerInLevel(helper);
        ServerPlayer sleeper = connected.player();
        armCallback();
        Runnable cleanup = () -> {
            callbackArmed = false;
            RespiteConfig.get().maxTimeLapseRate = savedRate;
            RespiteConfig.get().announceTimeLapse = savedAnnounce;
            MockPlayers.retire(sleeper);
        };
        int[] realTick = {0};
        long[] lastRealTick = {-1};
        Set<String> seenKeys = new HashSet<>();
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
            seenKeys.addAll(overlayKeys(connected));
            if (TimeLapseEngine.getEffectiveRate() > 1 && startEdges >= 1) {
                helper.assertTrue(callbackOldRate == 1 && callbackNewRate > 1,
                        "the callback must still fire the start edge with the announce gate off, got "
                                + callbackOldRate + " -> " + callbackNewRate);
                helper.assertTrue(seenKeys.stream().noneMatch(k -> k.startsWith("notification.respite.time_")),
                        "the announce gate off must suppress every time-lapse line, saw " + seenKeys);
                cleanup.run();
                helper.succeed();
            } else if (t >= 120) {
                helper.fail("the lapse never started within 120 real ticks");
            }
        }));
    }
}
