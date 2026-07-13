package com.rfizzle.respite.gametest;

import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.gametest.util.MockPlayers;
import com.rfizzle.respite.timelapse.TimeLapseEngine;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * In-world coverage for {@code design/SPEC.md} §2: the interval conversion
 * with its 1:1 saturation spend, vanilla-regen suspension in bed and resume on
 * wake, the Deep Sleep lunar gradient (its new-moon peak and a quarter-moon
 * mid-step), the wake-feedback lines at the three-heart threshold, the relaxed
 * arming gate, vanilla parity with the feature off, and total-preserving
 * compression under an active time-lapse.
 *
 * <p>The framework facts from {@link TimeLapseGameTest} apply here too, plus
 * one of this feature's own: mock players sleep genuinely but vanilla never
 * runs their {@code doTick} (their connection is not in the server's list), so
 * every test drives the body tick manually once per real tick — which is also
 * exactly the seam the conversion counts. Deterministic tests must not disable
 * the time-lapse to get a still night: with the lapse off, vanilla's skip
 * fires once a lone sleeper reaches deep sleep and would end the night at tick
 * 100. Instead they leave the lapse on (its skip suppression holds sleepers in
 * bed all night) and starve it with a zero tick budget, so world ticks stay
 * 1:1 with real ticks.
 */
public class RestfulSaturationGameTest implements FabricGameTest {

    private static final BlockPos BED_HEAD = new BlockPos(1, 2, 1);
    private static final BlockPos BED_FOOT = new BlockPos(1, 2, 2);
    /** An ordinary night — moon phase 0, nowhere near the new moon. */
    private static final long NIGHT_START = 13000L;
    /** Night of day 4 — moon phase 4, the new moon (Deep Sleep's peak). */
    private static final long NEW_MOON_NIGHT = 4 * 24000L + 13000L;
    /** Night of day 2 — moon phase 2, a quarter moon (the gradient's mid-step, ×1.5). */
    private static final long QUARTER_MOON_NIGHT = 2 * 24000L + 13000L;
    /** The interval range's legal floor — fast nights for the multi-conversion tests. */
    private static final int FAST_INTERVAL = 100;

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

    /**
     * A still night: clean player list, night time, a bed, and the time-lapse
     * budget-starved to ×1 so world ticks track real ticks. Returns the saved
     * budget for the cleanup.
     */
    private int setUpStillNight(GameTestHelper helper, long dayTime) {
        MockPlayers.retireLeaked(helper);
        helper.getLevel().setDayTime(dayTime);
        placeBed(helper);
        int savedBudget = RespiteConfig.get().timeLapseTickBudgetMs;
        RespiteConfig.get().timeLapseTickBudgetMs = 0;
        return savedBudget;
    }

    /** Primes the sleeper's vitals before bed: hurt, hungry-in-reserve, full bar. */
    private static void primeVitals(ServerPlayer player, int food, float saturation, float health) {
        player.getFoodData().setFoodLevel(food);
        player.getFoodData().setSaturation(saturation);
        player.setHealth(health);
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

    /** True exactly once per real server tick (see {@link TimeLapseGameTest}). */
    private static boolean newRealTick(long[] lastSeen) {
        long realTick = TimeLapseEngine.getRealTickCount();
        if (realTick == lastSeen[0]) {
            return false;
        }
        lastSeen[0] = realTick;
        return true;
    }

    /**
     * The action-bar (overlay) translation keys currently in the mock's
     * channel. The server suspends per-player connection flushing while the
     * levels tick (batch flushing) and resumes it afterwards — a packet sent
     * mid-tick is written but unflushed while a gametest listener runs, so
     * the channel is flushed here before reading.
     */
    private static java.util.List<String> overlayKeys(MockPlayers.Connected connected) {
        connected.channel().flush();
        java.util.List<String> keys = new java.util.ArrayList<>();
        for (Object message : connected.channel().outboundMessages()) {
            if (message instanceof ClientboundSystemChatPacket packet && packet.overlay()
                    && packet.content().getContents() instanceof TranslatableContents contents) {
                keys.add(contents.getKey());
            }
        }
        return keys;
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "restfulInterval", timeoutTicks = 800)
    public void armedSleeperConvertsAtTheDefaultInterval(GameTestHelper helper) {
        int savedBudget = setUpStillNight(helper, NIGHT_START);
        MockPlayers.Connected connected = MockPlayers.connectedServerPlayerInLevel(helper);
        ServerPlayer sleeper = connected.player();
        Runnable cleanup = () -> {
            RespiteConfig.get().timeLapseTickBudgetMs = savedBudget;
            MockPlayers.retire(sleeper);
        };
        int[] realTick = {0};
        int[] sleepTicks = {0};
        long[] lastRealTick = {-1};
        helper.onEachTick(() -> guarded(cleanup, () -> {
            if (!newRealTick(lastRealTick)) {
                return;
            }
            int t = ++realTick[0];
            if (t >= 700) {
                // fail (not framework timeout) so guarded() restores the config
                helper.fail("the 600-tick conversion checkpoint never fired; sleep ticks: "
                        + sleepTicks[0]);
            }
            if (t == 2) {
                primeVitals(sleeper, 20, 5.0f, 10.0f);
                sleepInBed(helper, sleeper);
                return;
            }
            if (t < 2 || !sleeper.isSleeping()) {
                return;
            }
            sleeper.doTick(); // the real-cadence body tick vanilla would run
            int slept = ++sleepTicks[0];
            if (slept < 600) {
                // vanilla's saturated fast regen would have healed every 10
                // ticks — a flat line here proves the suspension too
                helper.assertTrue(sleeper.getHealth() == 10.0f,
                        "no healing before the 600-tick interval; at sleep tick " + slept
                                + " health was " + sleeper.getHealth());
            } else if (slept == 600) {
                helper.assertTrue(sleeper.getHealth() == 11.0f,
                        "the 600th world tick must convert: expected 11.0 health, got "
                                + sleeper.getHealth());
                helper.assertTrue(sleeper.getFoodData().getSaturationLevel() == 4.0f,
                        "the conversion must spend exactly 1.0 saturation, got "
                                + sleeper.getFoodData().getSaturationLevel());
                helper.assertTrue(sleeper.getFoodData().getFoodLevel() == 20,
                        "the hunger bar itself must never drop overnight, got "
                                + sleeper.getFoodData().getFoodLevel());
                connected.channel().outboundMessages().clear();
                sleeper.stopSleepInBed(true, true);
                helper.assertTrue(!overlayKeys(connected).contains("notification.respite.rested")
                                && !overlayKeys(connected).contains("notification.respite.deep_rested"),
                        "1.0 health restored is under the three-heart threshold — no wake line");
                cleanup.run();
                helper.succeed();
            }
        }));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "restfulRegen", timeoutTicks = 300)
    public void vanillaRegenStandsDownInBedAndResumesOnWake(GameTestHelper helper) {
        int savedBudget = setUpStillNight(helper, NIGHT_START);
        ServerPlayer sleeper = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> {
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
            if (t == 2) {
                primeVitals(sleeper, 20, 5.0f, 10.0f);
                sleepInBed(helper, sleeper);
                return;
            }
            if (t > 2 && t <= 62) {
                sleeper.doTick();
                // full bar, saturation up, hurt: vanilla's fast regen would heal
                // every 10 ticks — asleep, it must not
                helper.assertTrue(sleeper.getHealth() == 10.0f,
                        "vanilla regen must stand down in bed; at tick " + t + " health was "
                                + sleeper.getHealth());
                if (t == 62) {
                    sleeper.stopSleepInBed(true, true);
                }
            } else if (t > 62 && t <= 130) {
                sleeper.doTick(); // awake body tick — the vanilla food tick runs again
                if (sleeper.getHealth() > 10.0f) {
                    cleanup.run();
                    helper.succeed();
                } else if (t == 130) {
                    helper.fail("vanilla regen never resumed after waking; health still "
                            + sleeper.getHealth());
                }
            }
        }));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "restfulDeep", timeoutTicks = 500)
    public void newMoonConversionHealsDoubleAndDeepensTheWakeLine(GameTestHelper helper) {
        int savedBudget = setUpStillNight(helper, NEW_MOON_NIGHT);
        int savedInterval = RespiteConfig.get().restfulHealIntervalTicks;
        RespiteConfig.get().restfulHealIntervalTicks = FAST_INTERVAL;
        MockPlayers.Connected connected = MockPlayers.connectedServerPlayerInLevel(helper);
        ServerPlayer sleeper = connected.player();
        Runnable cleanup = () -> {
            RespiteConfig.get().timeLapseTickBudgetMs = savedBudget;
            RespiteConfig.get().restfulHealIntervalTicks = savedInterval;
            MockPlayers.retire(sleeper);
        };
        int[] realTick = {0};
        int[] sleepTicks = {0};
        long[] lastRealTick = {-1};
        helper.onEachTick(() -> guarded(cleanup, () -> {
            if (!newRealTick(lastRealTick)) {
                return;
            }
            int t = ++realTick[0];
            if (t >= 450) {
                // fail (not framework timeout) so guarded() restores the config
                helper.fail("the deep-conversion checkpoints never fired; sleep ticks: "
                        + sleepTicks[0]);
            }
            if (t == 2) {
                primeVitals(sleeper, 20, 10.0f, 10.0f);
                sleepInBed(helper, sleeper);
                return;
            }
            if (t < 2 || !sleeper.isSleeping()) {
                return;
            }
            sleeper.doTick();
            int slept = ++sleepTicks[0];
            if (slept == FAST_INTERVAL) {
                helper.assertTrue(sleeper.getHealth() == 12.0f,
                        "a new-moon conversion must heal 2.0 for the same saturation, got "
                                + sleeper.getHealth());
                helper.assertTrue(sleeper.getFoodData().getSaturationLevel() == 9.0f,
                        "the deep conversion still spends exactly 1.0 saturation, got "
                                + sleeper.getFoodData().getSaturationLevel());
            } else if (slept == 3 * FAST_INTERVAL) {
                // three deep conversions: 6.0 restored — the threshold exactly
                helper.assertTrue(sleeper.getHealth() == 16.0f,
                        "three deep conversions must restore 6.0, health was " + sleeper.getHealth());
                connected.channel().outboundMessages().clear();
                sleeper.stopSleepInBed(true, true);
                helper.assertTrue(overlayKeys(connected).contains("notification.respite.deep_rested"),
                        "a deep night at the threshold must say deeply rested; overlay keys: "
                                + overlayKeys(connected));
                cleanup.run();
                helper.succeed();
            }
        }));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "restfulGradient", timeoutTicks = 500)
    public void quarterMoonConversionHealsTheRampedAmountWithoutTheDeepLine(GameTestHelper helper) {
        int savedBudget = setUpStillNight(helper, QUARTER_MOON_NIGHT);
        int savedInterval = RespiteConfig.get().restfulHealIntervalTicks;
        RespiteConfig.get().restfulHealIntervalTicks = FAST_INTERVAL;
        MockPlayers.Connected connected = MockPlayers.connectedServerPlayerInLevel(helper);
        ServerPlayer sleeper = connected.player();
        Runnable cleanup = () -> {
            RespiteConfig.get().timeLapseTickBudgetMs = savedBudget;
            RespiteConfig.get().restfulHealIntervalTicks = savedInterval;
            MockPlayers.retire(sleeper);
        };
        int[] realTick = {0};
        int[] sleepTicks = {0};
        long[] lastRealTick = {-1};
        helper.onEachTick(() -> guarded(cleanup, () -> {
            if (!newRealTick(lastRealTick)) {
                return;
            }
            int t = ++realTick[0];
            if (t >= 450) {
                // fail (not framework timeout) so guarded() restores the config
                helper.fail("the gradient checkpoints never fired; sleep ticks: " + sleepTicks[0]);
            }
            if (t == 2) {
                primeVitals(sleeper, 20, 10.0f, 10.0f);
                sleepInBed(helper, sleeper);
                return;
            }
            if (t < 2 || !sleeper.isSleeping()) {
                return;
            }
            sleeper.doTick();
            int slept = ++sleepTicks[0];
            if (slept == FAST_INTERVAL) {
                // a quarter-moon conversion heals ×1.5 (10.0 → 11.5) for the same 1.0 saturation
                helper.assertTrue(sleeper.getHealth() == 11.5f,
                        "a quarter-moon conversion must heal 1.5 on the gradient, got "
                                + sleeper.getHealth());
                helper.assertTrue(sleeper.getFoodData().getSaturationLevel() == 9.0f,
                        "the gradient conversion still spends exactly 1.0 saturation, got "
                                + sleeper.getFoodData().getSaturationLevel());
            } else if (slept == 4 * FAST_INTERVAL) {
                // four ramped conversions: 6.0 restored — the threshold exactly, but no
                // new moon ran, so the crown stays put: the plain rested line, never deep
                helper.assertTrue(sleeper.getHealth() == 16.0f,
                        "four quarter-moon conversions must restore 6.0, health was "
                                + sleeper.getHealth());
                connected.channel().outboundMessages().clear();
                sleeper.stopSleepInBed(true, true);
                helper.assertTrue(overlayKeys(connected).contains("notification.respite.rested"),
                        "a bonus night at the threshold must say refreshed; overlay keys: "
                                + overlayKeys(connected));
                helper.assertTrue(!overlayKeys(connected).contains("notification.respite.deep_rested"),
                        "only the new moon deepens the line — a quarter moon must not upgrade");
                cleanup.run();
                helper.succeed();
            }
        }));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "restfulWake", timeoutTicks = 800)
    public void wakingThreeHeartsRicherSaysRefreshed(GameTestHelper helper) {
        int savedBudget = setUpStillNight(helper, NIGHT_START);
        int savedInterval = RespiteConfig.get().restfulHealIntervalTicks;
        RespiteConfig.get().restfulHealIntervalTicks = FAST_INTERVAL;
        MockPlayers.Connected connected = MockPlayers.connectedServerPlayerInLevel(helper);
        ServerPlayer sleeper = connected.player();
        Runnable cleanup = () -> {
            RespiteConfig.get().timeLapseTickBudgetMs = savedBudget;
            RespiteConfig.get().restfulHealIntervalTicks = savedInterval;
            MockPlayers.retire(sleeper);
        };
        int[] realTick = {0};
        int[] sleepTicks = {0};
        long[] lastRealTick = {-1};
        helper.onEachTick(() -> guarded(cleanup, () -> {
            if (!newRealTick(lastRealTick)) {
                return;
            }
            int t = ++realTick[0];
            if (t >= 750) {
                // fail (not framework timeout) so guarded() restores the config
                helper.fail("the wake-line checkpoint never fired; sleep ticks: " + sleepTicks[0]);
            }
            if (t == 2) {
                primeVitals(sleeper, 20, 10.0f, 10.0f);
                sleepInBed(helper, sleeper);
                return;
            }
            if (t < 2 || !sleeper.isSleeping()) {
                return;
            }
            sleeper.doTick();
            if (++sleepTicks[0] == 6 * FAST_INTERVAL + 20) {
                // six ordinary conversions banked: 6.0 restored, none of them deep
                helper.assertTrue(sleeper.getHealth() == 16.0f,
                        "six conversions must restore 6.0, health was " + sleeper.getHealth());
                connected.channel().outboundMessages().clear();
                sleeper.stopSleepInBed(true, true);
                helper.assertTrue(overlayKeys(connected).contains("notification.respite.rested"),
                        "restoring three hearts must say refreshed; overlay keys: "
                                + overlayKeys(connected));
                helper.assertTrue(!overlayKeys(connected).contains("notification.respite.deep_rested"),
                        "no deep conversion ran — the line must not upgrade");
                cleanup.run();
                helper.succeed();
            }
        }));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "restfulDisabled", timeoutTicks = 400)
    public void disabledConfigLeavesSleepVanilla(GameTestHelper helper) {
        int savedBudget = setUpStillNight(helper, NIGHT_START);
        int savedInterval = RespiteConfig.get().restfulHealIntervalTicks;
        boolean savedEnable = RespiteConfig.get().enableRestfulSaturation;
        RespiteConfig.get().restfulHealIntervalTicks = FAST_INTERVAL;
        RespiteConfig.get().enableRestfulSaturation = false;
        GameRules.BooleanValue regenRule = helper.getLevel().getGameRules()
                .getRule(GameRules.RULE_NATURAL_REGENERATION);
        boolean savedRegen = regenRule.get();
        regenRule.set(false, helper.getLevel().getServer());
        ServerPlayer sleeper = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> {
            RespiteConfig.get().timeLapseTickBudgetMs = savedBudget;
            RespiteConfig.get().restfulHealIntervalTicks = savedInterval;
            RespiteConfig.get().enableRestfulSaturation = savedEnable;
            regenRule.set(savedRegen, helper.getLevel().getServer());
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
                primeVitals(sleeper, 20, 5.0f, 10.0f);
                sleepInBed(helper, sleeper);
                return;
            }
            if (t > 2 && t <= 120) {
                sleeper.doTick();
                // regen gamerule off, feature off: nothing may move — a stray
                // conversion at the 100-tick interval would show up right here
                helper.assertTrue(sleeper.getHealth() == 10.0f,
                        "feature off must never convert; at tick " + t + " health was "
                                + sleeper.getHealth());
                helper.assertTrue(sleeper.getFoodData().getSaturationLevel() == 5.0f,
                        "feature off must never spend saturation, got "
                                + sleeper.getFoodData().getSaturationLevel());
                if (t == 120) {
                    regenRule.set(true, helper.getLevel().getServer());
                }
            } else if (t > 120 && t <= 200) {
                sleeper.doTick();
                // regen rule back on, feature still off: vanilla's fast regen
                // must heal IN BED — the suspension is inert when disabled
                if (sleeper.getHealth() > 10.0f) {
                    cleanup.run();
                    helper.succeed();
                } else if (t == 200) {
                    helper.fail("with the feature off vanilla regen must run in bed; health still "
                            + sleeper.getHealth());
                }
            }
        }));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "restfulGate", timeoutTicks = 500)
    public void relaxedGateArmsAtEighteenNotSeventeen(GameTestHelper helper) {
        int savedBudget = setUpStillNight(helper, NIGHT_START);
        int savedInterval = RespiteConfig.get().restfulHealIntervalTicks;
        boolean savedGate = RespiteConfig.get().restfulRequiresFullHunger;
        RespiteConfig.get().restfulHealIntervalTicks = FAST_INTERVAL;
        RespiteConfig.get().restfulRequiresFullHunger = false;
        ServerPlayer sleeper = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> {
            RespiteConfig.get().timeLapseTickBudgetMs = savedBudget;
            RespiteConfig.get().restfulHealIntervalTicks = savedInterval;
            RespiteConfig.get().restfulRequiresFullHunger = savedGate;
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
                primeVitals(sleeper, 18, 5.0f, 10.0f);
                sleepInBed(helper, sleeper);
            } else if (t > 2 && t <= 110) {
                sleeper.doTick();
                if (t == 110) {
                    // food 18 armed under the relaxed gate: one conversion by now
                    helper.assertTrue(sleeper.getHealth() == 11.0f,
                            "food 18 must arm when the gate is relaxed; health was "
                                    + sleeper.getHealth());
                    sleeper.stopSleepInBed(true, true);
                }
            } else if (t == 112) {
                primeVitals(sleeper, 17, 4.0f, 10.0f);
                sleepInBed(helper, sleeper);
            } else if (t > 112 && t <= 225) {
                sleeper.doTick();
                helper.assertTrue(sleeper.getHealth() == 10.0f,
                        "food 17 must not arm even relaxed; at tick " + t + " health was "
                                + sleeper.getHealth());
                if (t == 225) {
                    cleanup.run();
                    helper.succeed();
                }
            }
        }));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "restfulCompression", timeoutTicks = 1500)
    public void timeLapseCompressesTheWaitButNotTheTotals(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        helper.getLevel().setDayTime(NIGHT_START);
        placeBed(helper);
        int savedRate = RespiteConfig.get().maxTimeLapseRate;
        int savedInterval = RespiteConfig.get().restfulHealIntervalTicks;
        RespiteConfig.get().maxTimeLapseRate = 4; // the framework-safe cap TimeLapseGameTest uses
        RespiteConfig.get().restfulHealIntervalTicks = FAST_INTERVAL;
        ServerPlayer sleeper = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> {
            RespiteConfig.get().maxTimeLapseRate = savedRate;
            RespiteConfig.get().restfulHealIntervalTicks = savedInterval;
            MockPlayers.retire(sleeper);
        };
        int[] realTick = {0};
        int[] sleepRealTicks = {0};
        int[] extrasSum = {0};
        long[] dayTimeAtSleep = {0};
        long[] lastRealTick = {-1};
        helper.onEachTick(() -> guarded(cleanup, () -> {
            if (!newRealTick(lastRealTick)) {
                return;
            }
            int t = ++realTick[0];
            if (t >= 900) {
                // fail (not framework timeout) so guarded() restores the config
                helper.fail("the compression checkpoint never fired; world ticks slept: "
                        + (helper.getLevel().getDayTime() - dayTimeAtSleep[0]));
            }
            if (t == 2) {
                primeVitals(sleeper, 20, 10.0f, 10.0f);
                sleepInBed(helper, sleeper);
                dayTimeAtSleep[0] = helper.getLevel().getDayTime();
                return;
            }
            if (t < 2 || !sleeper.isSleeping()) {
                return;
            }
            sleeper.doTick(); // real-cadence stand-in; the engine adds one per extra tick
            sleepRealTicks[0]++;
            extrasSum[0] += TimeLapseEngine.getEffectiveRate() - 1;
            long worldTicksSlept = helper.getLevel().getDayTime() - dayTimeAtSleep[0];
            // sample away from interval boundaries so an in-flight extra-tick
            // batch can't straddle the assertion
            if (worldTicksSlept >= 620 && worldTicksSlept % FAST_INTERVAL >= 20
                    && worldTicksSlept % FAST_INTERVAL <= 80) {
                float expected = 10.0f + (int) (worldTicksSlept / FAST_INTERVAL);
                helper.assertTrue(sleeper.getHealth() == expected,
                        "heal totals must ride world ticks: " + worldTicksSlept
                                + " world ticks slept expect health " + expected + ", got "
                                + sleeper.getHealth());
                helper.assertTrue(extrasSum[0] > 10,
                        "the lapse must actually have accelerated, extras run: " + extrasSum[0]);
                helper.assertTrue(sleepRealTicks[0] < worldTicksSlept,
                        "the real-time wait must compress: " + sleepRealTicks[0]
                                + " real ticks for " + worldTicksSlept + " world ticks");
                cleanup.run();
                helper.succeed();
            }
        }));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "restfulUnarmed", timeoutTicks = 400)
    public void unarmedSleeperRegenStaysFrozenUntilWake(GameTestHelper helper) {
        // Pins the deliberate reading of SPEC §2.4–5: suspension keys on
        // sleeping, not on being armed. Food 19 under the strict default gate
        // is above vanilla's regen floor but below the arming bar — vanilla
        // regen must NOT run in bed (world-tick-cadence regen across an
        // accelerated night would dwarf the conversion), and must resume on
        // wake as usual.
        int savedBudget = setUpStillNight(helper, NIGHT_START);
        int savedInterval = RespiteConfig.get().restfulHealIntervalTicks;
        RespiteConfig.get().restfulHealIntervalTicks = FAST_INTERVAL;
        ServerPlayer sleeper = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> {
            RespiteConfig.get().timeLapseTickBudgetMs = savedBudget;
            RespiteConfig.get().restfulHealIntervalTicks = savedInterval;
            MockPlayers.retire(sleeper);
        };
        int[] realTick = {0};
        long[] lastRealTick = {-1};
        helper.onEachTick(() -> guarded(cleanup, () -> {
            if (!newRealTick(lastRealTick)) {
                return;
            }
            int t = ++realTick[0];
            if (t >= 350) {
                // fail (not framework timeout) so guarded() restores the config
                helper.fail("the unarmed regen-resume checkpoint never fired");
            }
            if (t == 2) {
                primeVitals(sleeper, 19, 5.0f, 10.0f);
                sleepInBed(helper, sleeper);
                return;
            }
            if (t > 2 && t <= 120) {
                sleeper.doTick();
                // past the 100-tick interval: no conversion (unarmed) and no
                // vanilla 80-tick regen (suspended) — the night is frozen
                helper.assertTrue(sleeper.getHealth() == 10.0f,
                        "an unarmed sleeper must neither convert nor regen; at tick " + t
                                + " health was " + sleeper.getHealth());
                helper.assertTrue(sleeper.getFoodData().getSaturationLevel() == 5.0f,
                        "an unarmed sleeper's saturation must stay frozen, got "
                                + sleeper.getFoodData().getSaturationLevel());
                if (t == 120) {
                    sleeper.stopSleepInBed(true, true);
                }
            } else if (t > 120 && t <= 250) {
                sleeper.doTick(); // awake: food 19 sits in vanilla's 80-tick regen band
                if (sleeper.getHealth() > 10.0f) {
                    cleanup.run();
                    helper.succeed();
                } else if (t == 250) {
                    helper.fail("vanilla regen must resume for the unarmed player on wake; health still "
                            + sleeper.getHealth());
                }
            }
        }));
    }
}
