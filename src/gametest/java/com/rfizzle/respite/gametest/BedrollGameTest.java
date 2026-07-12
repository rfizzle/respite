package com.rfizzle.respite.gametest;

import com.rfizzle.respite.bedroll.Bedroll;
import com.rfizzle.respite.bedroll.BedrollBlock;
import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.gametest.util.MockPlayers;
import com.rfizzle.respite.registry.RespiteRegistry;
import com.rfizzle.respite.timelapse.TimeLapseEngine;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * In-world coverage for the bedroll ({@code design/SPEC.md} §7). It leans on the
 * same framework facts as {@link RestfulSaturationGameTest}: mock players sleep
 * genuinely, their {@code doTick} is driven by hand once per real tick, and the
 * time-lapse is budget-starved to ×1 so world ticks track real ticks.
 *
 * <p>Because the bedroll is a real {@link net.minecraft.world.level.block.BedBlock},
 * vanilla's per-tick sleeper-eject leaves a bedroll sleeper alone with no mixin;
 * the tests here pin the Respite-specific behavior on top: no spawn set, the
 * rest clock cleared, half-strength healing, and the roll-up on wake.
 */
public class BedrollGameTest implements FabricGameTest {

    private static final BlockPos BEDROLL_POS = new BlockPos(1, 2, 1);
    /** An ordinary night — moon phase 0. */
    private static final long NIGHT_START = 13000L;
    /** Night of day 4 — moon phase 4, the new moon (Deep Sleep). */
    private static final long NEW_MOON_NIGHT = 4 * 24000L + 13000L;
    private static final int FAST_INTERVAL = 100;

    private void placeBedroll(GameTestHelper helper) {
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        helper.setBlock(BEDROLL_POS, RespiteRegistry.BEDROLL.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    /** Teleports the mock onto the bedroll and puts them to sleep the Respite way. */
    private void sleepInBedroll(GameTestHelper helper, ServerPlayer player) {
        BlockPos pos = helper.absolutePos(BEDROLL_POS);
        player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        Bedroll.sleep(player, pos);
        helper.assertTrue(player.isSleeping(), "the mock must genuinely sleep in the bedroll");
    }

    private int setUpStillNight(GameTestHelper helper, long dayTime) {
        MockPlayers.retireLeaked(helper);
        helper.getLevel().setDayTime(dayTime);
        placeBedroll(helper);
        int savedBudget = RespiteConfig.get().timeLapseTickBudgetMs;
        RespiteConfig.get().timeLapseTickBudgetMs = 0;
        return savedBudget;
    }

    private static void primeVitals(ServerPlayer player, int food, float saturation, float health) {
        player.getFoodData().setFoodLevel(food);
        player.getFoodData().setSaturation(saturation);
        player.setHealth(health);
    }

    private static void guarded(Runnable cleanup, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            cleanup.run();
            throw t;
        }
    }

    private static boolean newRealTick(long[] lastSeen) {
        long realTick = TimeLapseEngine.getRealTickCount();
        if (realTick == lastSeen[0]) {
            return false;
        }
        lastSeen[0] = realTick;
        return true;
    }

    private static long ticksSinceRest(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
    }

    /**
     * The load-bearing claim: a bedroll sleep never sets spawn and clears the
     * rest clock (§4/§7), and the sleeper is never ejected — a genuine bed keeps
     * vanilla's {@code checkBedExists} eject satisfied across real ticks with no
     * mixin. Waking then rolls the bedroll back into the inventory.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "bedrollSleep", timeoutTicks = 300)
    public void bedrollSleepSetsNoSpawnClearsRestAndRollsUp(GameTestHelper helper) {
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
                // A stale rest clock and no prior spawn: the bedroll must clear
                // the first and never set the second.
                sleeper.getStats().setValue(sleeper, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), 5 * 24000);
                helper.assertTrue(sleeper.getRespawnPosition() == null, "precondition: no spawn set yet");
                sleepInBedroll(helper, sleeper);
                helper.assertTrue(ticksSinceRest(sleeper) == 0,
                        "starting to sleep must reset TIME_SINCE_REST, got " + ticksSinceRest(sleeper));
                helper.assertTrue(sleeper.getRespawnPosition() == null,
                        "a bedroll must never set spawn, got " + sleeper.getRespawnPosition());
                return;
            }
            if (t > 2 && t <= 20) {
                // No manual doTick: the sleeper must simply stay asleep — a real
                // bed is never ejected by vanilla's per-tick check.
                helper.assertTrue(sleeper.isSleeping(),
                        "a bedroll sleeper must not be ejected; woke at tick " + t);
                if (t == 20) {
                    sleeper.stopSleepInBed(true, true);
                    // Roll-up: the block is gone and the item is back in the pack.
                    helper.assertTrue(!(helper.getBlockState(BEDROLL_POS).getBlock() instanceof BedrollBlock),
                            "waking must remove the bedroll block");
                    helper.assertTrue(sleeper.getInventory().countItem(RespiteRegistry.BEDROLL.asItem()) == 1,
                            "waking must return exactly one bedroll to the inventory, got "
                                    + sleeper.getInventory().countItem(RespiteRegistry.BEDROLL.asItem()));
                    cleanup.run();
                    helper.succeed();
                }
            }
        }));
    }

    /**
     * The item's one-action unroll-and-sleep must sleep <em>at</em> the placed
     * bedroll even on replaceable ground (grass, ferns, snow). Regression guard
     * for the placement position being recomputed after placement — on a
     * replaceable tile that shifts the sleep one block into the air, where
     * vanilla's per-tick eject then wakes the player.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "bedrollAutoUse", timeoutTicks = 200)
    public void itemAutoUseSleepsAtTheBedrollOnReplaceableGround(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        helper.getLevel().setDayTime(NIGHT_START);
        int savedBudget = RespiteConfig.get().timeLapseTickBudgetMs;
        RespiteConfig.get().timeLapseTickBudgetMs = 0;
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        // A replaceable tuft on the click tile: right-clicking it places the
        // bedroll onto that same tile, not the space above.
        BlockPos clickRel = new BlockPos(1, 2, 1);
        helper.setBlock(clickRel, Blocks.SHORT_GRASS.defaultBlockState());
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> {
            RespiteConfig.get().timeLapseTickBudgetMs = savedBudget;
            MockPlayers.retire(player);
        };
        int[] realTick = {0};
        long[] lastRealTick = {-1};
        helper.onEachTick(() -> guarded(cleanup, () -> {
            if (!newRealTick(lastRealTick)) {
                return;
            }
            if (++realTick[0] != 2) {
                return;
            }
            BlockPos abs = helper.absolutePos(clickRel);
            // Stand off the placement tile so the bedroll can be placed there.
            player.teleportTo(abs.getX() - 0.5, abs.getY(), abs.getZ() + 0.5);
            BlockHitResult hit = new BlockHitResult(
                    new Vec3(abs.getX() + 0.5, abs.getY() + 0.1, abs.getZ() + 0.5),
                    Direction.UP, abs, false);
            ItemStack stack = new ItemStack(RespiteRegistry.BEDROLL);
            UseOnContext context =
                    new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit);

            InteractionResult result = RespiteRegistry.BEDROLL.asItem().useOn(context);

            helper.assertTrue(result.consumesAction(), "auto-use must place and act, got " + result);
            helper.assertTrue(helper.getBlockState(clickRel).getBlock() instanceof BedrollBlock,
                    "the bedroll must be placed on the replaceable tile");
            helper.assertTrue(player.isSleeping(), "auto-use must start sleep in one action");
            BlockPos sleepPos = player.getSleepingPos().orElse(null);
            helper.assertTrue(abs.equals(sleepPos),
                    "must sleep at the bedroll " + abs + ", not the air above it; got " + sleepPos);
            cleanup.run();
            helper.succeed();
        }));
    }

    /** The promised "not obstructed" bed rule (§7.3): a blocked space above refuses sleep. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "bedrollObstructed", timeoutTicks = 100)
    public void obstructedBedrollRefusesSleep(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        helper.getLevel().setDayTime(NIGHT_START);
        placeBedroll(helper);
        // A solid block directly above the bedroll — vanilla's OBSTRUCTED case.
        helper.setBlock(BEDROLL_POS.above(), Blocks.STONE.defaultBlockState());
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> MockPlayers.retire(player);
        int[] realTick = {0};
        long[] lastRealTick = {-1};
        helper.onEachTick(() -> guarded(cleanup, () -> {
            if (!newRealTick(lastRealTick) || ++realTick[0] != 2) {
                return;
            }
            BlockPos abs = helper.absolutePos(BEDROLL_POS);
            player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
            Bedroll.sleep(player, abs);
            helper.assertTrue(!player.isSleeping(), "an obstructed bedroll must refuse sleep");
            cleanup.run();
            helper.succeed();
        }));
    }

    /** Half strength on an ordinary night: 0.5 HP per conversion vs a bed's 1.0 (§7). */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "bedrollHalf", timeoutTicks = 400)
    public void bedrollHealsAtHalfStrength(GameTestHelper helper) {
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
        int[] sleepTicks = {0};
        long[] lastRealTick = {-1};
        helper.onEachTick(() -> guarded(cleanup, () -> {
            if (!newRealTick(lastRealTick)) {
                return;
            }
            int t = ++realTick[0];
            if (t >= 350) {
                helper.fail("the half-strength checkpoint never fired; sleep ticks: " + sleepTicks[0]);
            }
            if (t == 2) {
                primeVitals(sleeper, 20, 10.0f, 10.0f);
                sleepInBedroll(helper, sleeper);
                return;
            }
            if (t < 2 || !sleeper.isSleeping()) {
                return;
            }
            sleeper.doTick();
            if (++sleepTicks[0] == FAST_INTERVAL) {
                helper.assertTrue(sleeper.getHealth() == 10.5f,
                        "a bedroll conversion must heal 0.5 (half of a bed's 1.0), got "
                                + sleeper.getHealth());
                helper.assertTrue(sleeper.getFoodData().getSaturationLevel() == 9.0f,
                        "half strength still spends exactly 1.0 saturation, got "
                                + sleeper.getFoodData().getSaturationLevel());
                cleanup.run();
                helper.succeed();
            }
        }));
    }

    /** New-moon stacking: a bedroll's 0.5 × Deep Sleep's 2.0 = 1.0 HP (§7). */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "bedrollDeep", timeoutTicks = 400)
    public void bedrollHalfStrengthStacksWithDeepSleep(GameTestHelper helper) {
        int savedBudget = setUpStillNight(helper, NEW_MOON_NIGHT);
        int savedInterval = RespiteConfig.get().restfulHealIntervalTicks;
        RespiteConfig.get().restfulHealIntervalTicks = FAST_INTERVAL;
        ServerPlayer sleeper = MockPlayers.serverPlayerInLevel(helper);
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
            if (t >= 350) {
                helper.fail("the new-moon bedroll checkpoint never fired; sleep ticks: " + sleepTicks[0]);
            }
            if (t == 2) {
                primeVitals(sleeper, 20, 10.0f, 10.0f);
                sleepInBedroll(helper, sleeper);
                return;
            }
            if (t < 2 || !sleeper.isSleeping()) {
                return;
            }
            sleeper.doTick();
            if (++sleepTicks[0] == FAST_INTERVAL) {
                helper.assertTrue(sleeper.getHealth() == 11.0f,
                        "a bedroll on a new moon heals 0.5×2.0 = 1.0 — a full bed's ordinary night, got "
                                + sleeper.getHealth());
                cleanup.run();
                helper.succeed();
            }
        }));
    }
}
