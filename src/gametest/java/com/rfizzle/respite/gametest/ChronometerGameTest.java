package com.rfizzle.respite.gametest;

import com.rfizzle.respite.Respite;
import com.rfizzle.respite.block.ChronometerBlock;
import com.rfizzle.respite.chronometer.ChronometerTime;
import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.gametest.util.MockPlayers;
import com.rfizzle.respite.registry.RespiteRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * In-world coverage for {@code design/SPEC.md} §5: the {@code /time set} sweep
 * across all 15 wire levels, the comparator's moon-fullness reading, the
 * sneak-cycled alarm hour, placement snap, the fixed-time Nether, the inspect
 * action-bar line in all three variants, and the config-disabled contract
 * (placed blocks keep functioning).
 *
 * <p>Every test that touches the shared overworld day time runs in its own
 * batch so the batches serialize instead of racing each other's clock.
 */
public class ChronometerGameTest implements FabricGameTest {

    private static final BlockPos FLOOR = new BlockPos(1, 1, 1);
    private static final BlockPos CHRONO = new BlockPos(1, 2, 1);
    private static final BlockPos WIRE = new BlockPos(2, 2, 1);
    private static final BlockPos COMPARATOR = new BlockPos(1, 2, 2);
    private static final BlockPos OUTPUT_WIRE = new BlockPos(1, 2, 3);

    /** Mid-band day time for a signal level, clear of both boundaries. */
    private static long midBand(int level) {
        return (level - 1) * 1600L + 800L;
    }

    /** Floor for the redstone parts, the chronometer, a reading wire, and a comparator chain. */
    private void buildRig(GameTestHelper helper) {
        for (int x = 0; x <= 3; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        helper.setBlock(CHRONO, RespiteRegistry.CHRONOMETER.defaultBlockState());
        helper.setBlock(WIRE, Blocks.REDSTONE_WIRE.defaultBlockState());
        // FACING points at the comparator's input side — the chronometer to its north.
        helper.setBlock(COMPARATOR, Blocks.COMPARATOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        helper.setBlock(OUTPUT_WIRE, Blocks.REDSTONE_WIRE.defaultBlockState());
    }

    private void assertReadings(GameTestHelper helper, int level) {
        int power = helper.getBlockState(CHRONO).getValue(ChronometerBlock.POWER);
        helper.assertTrue(power == level, "chronometer power was " + power + ", expected " + level);
        int wire = helper.getBlockState(WIRE).getValue(BlockStateProperties.POWER);
        helper.assertTrue(wire == level, "adjacent wire read " + wire + ", expected " + level);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chronometerSweep", timeoutTicks = 700)
    public void timeSetSweepDrivesAllFifteenLevels(GameTestHelper helper) {
        helper.getLevel().setDayTime(midBand(1));
        buildRig(helper);
        GameTestSequence sequence = helper.startSequence();
        for (int level = 1; level <= 15; level++) {
            int expected = level;
            sequence = sequence
                    .thenExecute(() -> helper.getLevel().setDayTime(midBand(expected)))
                    // the 20-tick re-check plus the comparator's own delay
                    .thenExecuteAfter(25, () -> assertReadings(helper, expected));
        }
        sequence.thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chronometerSteady", timeoutTicks = 200)
    public void sameBandHoldsTheStateSteady(GameTestHelper helper) {
        helper.getLevel().setDayTime(midBand(6));
        buildRig(helper);
        BlockState[] settled = new BlockState[1];
        helper.startSequence()
                .thenExecuteAfter(25, () -> {
                    assertReadings(helper, 6);
                    settled[0] = helper.getBlockState(CHRONO);
                })
                // three more re-check cycles inside the same 1,600-tick band
                .thenExecuteAfter(60, () -> {
                    helper.assertTrue(helper.getBlockState(CHRONO) == settled[0],
                            "state must not churn while the level is unchanged");
                    assertReadings(helper, 6);
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chronometerComparator", timeoutTicks = 300)
    public void comparatorReadsMoonFullness(GameTestHelper helper) {
        helper.getLevel().setDayTime(0);
        buildRig(helper);
        // dayTime → expected fullness. Each step also lands in a different signal
        // band (level 2 → 8 → 14) so the chronometer's power change notifies the
        // comparator to re-read the moon. Full → new → third quarter proves the
        // comparator actively drops to 0 and climbs again, not that it never powered.
        long[][] cases = {
                {midBand(2), 15},                 // day 0, full moon
                {4 * 24000L + midBand(8), 0},     // day 4, new moon
                {2 * 24000L + midBand(14), 8},    // day 2, third quarter
        };
        GameTestSequence sequence = helper.startSequence();
        for (long[] testCase : cases) {
            final long dayTime = testCase[0];
            final int expected = (int) testCase[1];
            sequence = sequence
                    .thenExecute(() -> helper.getLevel().setDayTime(dayTime))
                    .thenExecuteAfter(25, () -> {
                        int comparator = helper.getBlockState(OUTPUT_WIRE).getValue(BlockStateProperties.POWER);
                        helper.assertTrue(comparator == expected,
                                "comparator read " + comparator + ", expected moon fullness " + expected);
                    });
        }
        sequence.thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chronometerAlarm", timeoutTicks = 100)
    public void alarmCyclesWithSneakAndPersistsAcrossTick(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.SMOOTH_STONE.defaultBlockState());
        helper.setBlock(CHRONO, RespiteRegistry.CHRONOMETER.defaultBlockState());
        helper.assertTrue(
                helper.getBlockState(CHRONO).getValue(ChronometerBlock.ALARM_HOUR) == ChronometerTime.ALARM_OFF,
                "a fresh chronometer must place with its alarm off");

        MockPlayers.Connected connected = MockPlayers.connectedServerPlayerInLevel(helper);
        ServerPlayer player = connected.player();
        try {
            player.setShiftKeyDown(true);
            BlockPos abs = helper.absolutePos(CHRONO);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);

            // off → hour 0 → hour 1
            helper.getBlockState(CHRONO).useWithoutItem(helper.getLevel(), player, hit);
            int first = helper.getBlockState(CHRONO).getValue(ChronometerBlock.ALARM_HOUR);
            helper.assertTrue(first == 0, "first sneak-cycle must arm the alarm at hour 0, got " + first);

            helper.getBlockState(CHRONO).useWithoutItem(helper.getLevel(), player, hit);
            int second = helper.getBlockState(CHRONO).getValue(ChronometerBlock.ALARM_HOUR);
            helper.assertTrue(second == 1, "second sneak-cycle must advance to hour 1, got " + second);

            // the 20-tick re-check swaps only POWER — it must leave the alarm alone
            BlockPos abs2 = helper.absolutePos(CHRONO);
            helper.getLevel().getBlockState(abs2).tick(helper.getLevel(), abs2, helper.getLevel().getRandom());
            int afterTick = helper.getBlockState(CHRONO).getValue(ChronometerBlock.ALARM_HOUR);
            helper.assertTrue(afterTick == 1, "the re-check tick must preserve the alarm hour, got " + afterTick);
        } finally {
            MockPlayers.retire(player);
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chronometerPlace", timeoutTicks = 100)
    public void placementSetsTheCorrectLevelImmediately(GameTestHelper helper) {
        helper.getLevel().setDayTime(midBand(11));
        helper.setBlock(FLOOR, Blocks.SMOOTH_STONE.defaultBlockState());

        // The player-placement path computes the level before the block lands.
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(Respite.id("chronometer")));
        BlockPos absFloor = helper.absolutePos(FLOOR);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absFloor), Direction.UP, absFloor, false);
        BlockState placed = RespiteRegistry.CHRONOMETER
                .getStateForPlacement(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
        helper.assertTrue(placed != null && placed.getValue(ChronometerBlock.POWER) == 11,
                "player placement must land with the current level already set");
        player.discard();

        // A raw setBlock (command, piston, structure) snaps on the next tick.
        helper.setBlock(CHRONO, RespiteRegistry.CHRONOMETER.defaultBlockState());
        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    int power = helper.getBlockState(CHRONO).getValue(ChronometerBlock.POWER);
                    helper.assertTrue(power == 11, "raw placement snapped to " + power + ", expected 11");
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chronometerNether", timeoutTicks = 100)
    public void fixedTimeDimensionReadsZero(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            helper.fail("the gametest server did not create the Nether");
            return;
        }
        // Above the bedrock roof: always air, no terrain to fight. The re-check
        // is driven synchronously through the same public seam vanilla's tick
        // loop uses — the 20-tick chain itself is proven by the overworld tests,
        // and a remote dimension's chunk-ticket ticking is not this test's subject.
        BlockPos pos = new BlockPos(8, 130, 8);
        try {
            nether.setBlock(pos, RespiteRegistry.CHRONOMETER.defaultBlockState()
                    .setValue(ChronometerBlock.POWER, 7), 3);
            nether.getBlockState(pos).tick(nether, pos, nether.getRandom());
            BlockState state = nether.getBlockState(pos);
            helper.assertTrue(state.is(RespiteRegistry.CHRONOMETER), "chronometer must survive in the nether");
            int power = state.getValue(ChronometerBlock.POWER);
            helper.assertTrue(power == 0, "fixed-time dimension read " + power + ", expected 0 (still face)");
            int signal = state.getSignal(nether, pos, Direction.NORTH);
            helper.assertTrue(signal == 0, "fixed-time dimension emitted " + signal + ", expected 0");
        } finally {
            nether.removeBlock(pos, false);
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chronometerInspectDay", timeoutTicks = 100)
    public void inspectShowsClockAndSignalByDay(GameTestHelper helper) {
        long dayTime = 6800L; // 12:48 pm, level 5
        assertInspectLine(helper, dayTime, "notification.respite.chronometer", contents -> {
            helper.assertTrue(ChronometerTime.clockTime(dayTime).equals(contents.getArgs()[0]),
                    "clock arg was " + contents.getArgs()[0]);
            helper.assertTrue(Integer.valueOf(5).equals(contents.getArgs()[1]),
                    "signal arg was " + contents.getArgs()[1]);
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chronometerInspectNight", timeoutTicks = 100)
    public void inspectAddsTheMoonAtNight(GameTestHelper helper) {
        long dayTime = 18000L; // midnight of day 0 — a full moon, 4 nights out
        assertInspectLine(helper, dayTime, "notification.respite.chronometer_night", contents -> {
            Component moon = (Component) contents.getArgs()[2];
            String moonKey = ((TranslatableContents) moon.getContents()).getKey();
            helper.assertTrue("moon.respite.full".equals(moonKey), "moon arg key was " + moonKey);
            helper.assertTrue(Integer.valueOf(4).equals(contents.getArgs()[3]),
                    "countdown arg was " + contents.getArgs()[3]);
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chronometerInspectNewMoon", timeoutTicks = 100)
    public void inspectAnnouncesTheNewMoon(GameTestHelper helper) {
        long dayTime = 4 * 24000L + 18000L; // midnight, moon phase 4 — the new moon
        assertInspectLine(helper, dayTime, "notification.respite.chronometer_new_moon", contents -> {
        });
    }

    /** Right-clicks a placed chronometer and asserts the action-bar packet's key and args. */
    private void assertInspectLine(GameTestHelper helper, long dayTime, String expectedKey,
            java.util.function.Consumer<TranslatableContents> argAssertions) {
        helper.getLevel().setDayTime(dayTime);
        helper.setBlock(FLOOR, Blocks.SMOOTH_STONE.defaultBlockState());
        helper.setBlock(CHRONO, RespiteRegistry.CHRONOMETER.defaultBlockState());

        MockPlayers.Connected connected = MockPlayers.connectedServerPlayerInLevel(helper);
        ServerPlayer player = connected.player();
        try {
            BlockPos abs = helper.absolutePos(CHRONO);
            player.teleportTo(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() - 1.5);
            connected.channel().outboundMessages().clear();

            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
            helper.getBlockState(CHRONO).useWithoutItem(helper.getLevel(), player, hit);

            // displayClientMessage(..., true) rides the system-chat packet with
            // the overlay (action bar) flag set.
            TranslatableContents line = null;
            for (Object message : connected.channel().outboundMessages()) {
                if (message instanceof ClientboundSystemChatPacket packet && packet.overlay()
                        && packet.content().getContents() instanceof TranslatableContents contents) {
                    line = contents;
                }
            }
            helper.assertTrue(line != null, "inspect must send an action-bar line");
            helper.assertTrue(expectedKey.equals(line.getKey()),
                    "line key was " + line.getKey() + ", expected " + expectedKey);
            argAssertions.accept(line);
        } finally {
            MockPlayers.retire(player);
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chronometerConfig", timeoutTicks = 200)
    public void disabledConfigLeavesPlacedBlocksFunctioning(GameTestHelper helper) {
        boolean saved = RespiteConfig.get().enableChronometer;
        RespiteConfig.get().enableChronometer = false;
        helper.getLevel().setDayTime(midBand(9));
        buildRig(helper);
        helper.startSequence()
                // restore inside a finally so a failed assertion can't leave the
                // toggle poisoned for the rest of the gametest run
                .thenExecuteAfter(25, () -> {
                    try {
                        // The disable is a recipe gate only — a placed block keeps its
                        // wire signal, its moon comparator, and its alarm (SPEC §5).
                        assertReadings(helper, 9);
                        int comparator = helper.getBlockState(OUTPUT_WIRE).getValue(BlockStateProperties.POWER);
                        helper.assertTrue(comparator == 15,
                                "disabled config: comparator read " + comparator + ", expected moon fullness 15");

                        helper.setBlock(CHRONO, helper.getBlockState(CHRONO).setValue(ChronometerBlock.ALARM_HOUR, 6));
                        BlockPos abs = helper.absolutePos(CHRONO);
                        helper.getLevel().getBlockState(abs).tick(helper.getLevel(), abs, helper.getLevel().getRandom());
                        int alarm = helper.getBlockState(CHRONO).getValue(ChronometerBlock.ALARM_HOUR);
                        helper.assertTrue(alarm == 6,
                                "disabled config: a placed block must keep its alarm, got " + alarm);
                    } finally {
                        RespiteConfig.get().enableChronometer = saved;
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chronometerRecipe", timeoutTicks = 100)
    public void recipeAndUnlockAdvancementShipUnderDefaultConfig(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.getRecipeManager().byKey(Respite.id("chronometer")).isPresent(),
                "the chronometer recipe must load under the default (enabled) config");
        helper.assertTrue(server.getAdvancements().get(Respite.id("recipes/redstone/chronometer")) != null,
                "the recipe-unlock advancement must load under the default config");
        helper.succeed();
    }
}
