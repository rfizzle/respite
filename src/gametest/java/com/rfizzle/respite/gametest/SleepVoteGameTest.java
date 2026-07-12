package com.rfizzle.respite.gametest;

import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.gametest.util.MockPlayers;
import com.rfizzle.respite.sleepvote.SleepVoteLines;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * In-world coverage for {@code design/SPEC.md} §1's Sleep whisper: the chat
 * (non-overlay) line on a bed enter with its {@code sleeping/total} share, the
 * still-sleeping off-by-one on a leave (the leaver is never counted among the
 * sleepers), the mass-wake suppression once the night is over, and vanilla
 * parity with {@code announceSleepVote} off.
 *
 * <p>The framework facts from {@link RestfulSaturationGameTest} apply: mock
 * players sleep genuinely but vanilla never runs their {@code doTick}, and a
 * still night is held by leaving the time-lapse on (its skip suppression keeps
 * sleepers in bed) while starving its budget to zero so the world clock never
 * advances the night out from under the assertions. The whisper itself fires
 * synchronously from {@code startSleepInBed}/{@code stopSleepInBed}, so each
 * checkpoint reads the mock's packet channel right after the transition.
 */
public class SleepVoteGameTest implements FabricGameTest {

    private static final BlockPos BED_HEAD = new BlockPos(1, 2, 1);
    private static final BlockPos BED_FOOT = new BlockPos(1, 2, 2);
    /** An ordinary night — sleep is eligible, the time-lapse holds the night still. */
    private static final long NIGHT_START = 13000L;
    /** Full daylight — the night is over, no leave line should be sent. */
    private static final long DAY_TIME = 1000L;

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

    /** A still night: clean player list, night time, a bed, budget-starved lapse. Returns the saved budget. */
    private int setUpStillNight(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        helper.getLevel().setDayTime(NIGHT_START);
        placeBed(helper);
        int savedBudget = RespiteConfig.get().timeLapseTickBudgetMs;
        RespiteConfig.get().timeLapseTickBudgetMs = 0;
        return savedBudget;
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
     * The chat (non-overlay) translatable contents for {@code key} in the mock's
     * channel, or null if none. The server batch-flushes connections while the
     * levels tick, so a packet sent mid-tick is written but unflushed until the
     * channel is flushed here before reading.
     */
    private static TranslatableContents chatContents(MockPlayers.Connected connected, String key) {
        connected.channel().flush();
        for (Object message : connected.channel().outboundMessages()) {
            if (message instanceof ClientboundSystemChatPacket packet && !packet.overlay()
                    && packet.content().getContents() instanceof TranslatableContents contents
                    && contents.getKey().equals(key)) {
                return contents;
            }
        }
        return null;
    }

    /** Every chat (non-overlay) translatable key currently in the mock's channel. */
    private static List<String> chatKeys(MockPlayers.Connected connected) {
        connected.channel().flush();
        List<String> keys = new ArrayList<>();
        for (Object message : connected.channel().outboundMessages()) {
            if (message instanceof ClientboundSystemChatPacket packet && !packet.overlay()
                    && packet.content().getContents() instanceof TranslatableContents contents) {
                keys.add(contents.getKey());
            }
        }
        return keys;
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "sleepVoteEnterLeave", timeoutTicks = 200)
    public void enterAndLeaveWhisperTheShareExcludingTheLeaver(GameTestHelper helper) {
        int savedBudget = setUpStillNight(helper);
        MockPlayers.Connected sleeperC = MockPlayers.connectedServerPlayerInLevel(helper);
        MockPlayers.Connected awakeC = MockPlayers.connectedServerPlayerInLevel(helper);
        ServerPlayer sleeper = sleeperC.player();
        BlockPos awakePos = helper.absolutePos(new BlockPos(2, 2, 2));
        awakeC.player().teleportTo(awakePos.getX() + 0.5, awakePos.getY(), awakePos.getZ() + 0.5);
        Runnable cleanup = () -> {
            RespiteConfig.get().timeLapseTickBudgetMs = savedBudget;
            MockPlayers.retire(sleeper);
            MockPlayers.retire(awakeC.player());
        };
        int[] step = {0};
        helper.onEachTick(() -> guarded(cleanup, () -> {
            switch (++step[0]) {
                case 2 -> {
                    sleeperC.channel().outboundMessages().clear();
                    awakeC.channel().outboundMessages().clear();
                    sleepInBed(helper, sleeper);
                    // Enter: one asleep of two present — and it reaches the whole
                    // level, not just the actor.
                    TranslatableContents enter = chatContents(sleeperC, SleepVoteLines.ENTER_KEY);
                    helper.assertTrue(enter != null, "entering a bed must whisper a chat line; keys: "
                            + chatKeys(sleeperC));
                    assertCounts(helper, enter, 1, 2);
                    helper.assertTrue(chatContents(awakeC, SleepVoteLines.ENTER_KEY) != null,
                            "the whisper must broadcast to the whole level, not just the sleeper");
                }
                case 4 -> {
                    sleeperC.channel().outboundMessages().clear();
                    // The leaver is still isSleeping() when STOP fires — the line
                    // must report zero asleep, not one.
                    sleeper.stopSleepInBed(true, true);
                    TranslatableContents leave = chatContents(sleeperC, SleepVoteLines.LEAVE_KEY);
                    helper.assertTrue(leave != null, "leaving a bed at night must whisper a chat line; keys: "
                            + chatKeys(sleeperC));
                    assertCounts(helper, leave, 0, 2);
                    cleanup.run();
                    helper.succeed();
                }
                default -> {
                    if (step[0] >= 60) {
                        helper.fail("the enter/leave phase machine stalled");
                    }
                }
            }
        }));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "sleepVoteDayLeave", timeoutTicks = 200)
    public void leavingAfterTheNightEndsIsSilent(GameTestHelper helper) {
        int savedBudget = setUpStillNight(helper);
        MockPlayers.Connected sleeperC = MockPlayers.connectedServerPlayerInLevel(helper);
        MockPlayers.Connected awakeC = MockPlayers.connectedServerPlayerInLevel(helper);
        ServerPlayer sleeper = sleeperC.player();
        BlockPos awakePos = helper.absolutePos(new BlockPos(2, 2, 2));
        awakeC.player().teleportTo(awakePos.getX() + 0.5, awakePos.getY(), awakePos.getZ() + 0.5);
        Runnable cleanup = () -> {
            RespiteConfig.get().timeLapseTickBudgetMs = savedBudget;
            MockPlayers.retire(sleeper);
            MockPlayers.retire(awakeC.player());
        };
        int[] step = {0};
        boolean[] dayForced = {false};
        helper.onEachTick(() -> guarded(cleanup, () -> {
            int t = ++step[0];
            if (t == 2) {
                sleepInBed(helper, sleeper);
                return;
            }
            if (t == 4) {
                // Push past dawn. isNight() is derived from sky brightness, which
                // the level only recomputes on tick, so the wake must wait for the
                // sky to catch up — exactly as a real dawn wake lands downstream of
                // the night ending, never on the same tick the clock rolls over.
                helper.getLevel().setDayTime(DAY_TIME);
                dayForced[0] = true;
                return;
            }
            if (dayForced[0]) {
                if (helper.getLevel().isNight()) {
                    if (t >= 40) {
                        helper.fail("forced day time never registered as day");
                    }
                    return;
                }
                // The night is over: leaving the bed now — whether the crew woke on
                // their own or by this explicit call — must whisper nothing.
                if (sleeper.isSleeping()) {
                    sleeper.stopSleepInBed(true, true);
                }
                helper.assertTrue(!chatKeys(sleeperC).contains(SleepVoteLines.LEAVE_KEY),
                        "a leave after the night ends must stay silent; keys: " + chatKeys(sleeperC));
                cleanup.run();
                helper.succeed();
            }
        }));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "sleepVoteDisabled", timeoutTicks = 200)
    public void disabledConfigWhispersNothing(GameTestHelper helper) {
        int savedBudget = setUpStillNight(helper);
        boolean savedEnable = RespiteConfig.get().announceSleepVote;
        RespiteConfig.get().announceSleepVote = false;
        MockPlayers.Connected sleeperC = MockPlayers.connectedServerPlayerInLevel(helper);
        MockPlayers.Connected awakeC = MockPlayers.connectedServerPlayerInLevel(helper);
        ServerPlayer sleeper = sleeperC.player();
        BlockPos awakePos = helper.absolutePos(new BlockPos(2, 2, 2));
        awakeC.player().teleportTo(awakePos.getX() + 0.5, awakePos.getY(), awakePos.getZ() + 0.5);
        Runnable cleanup = () -> {
            RespiteConfig.get().timeLapseTickBudgetMs = savedBudget;
            RespiteConfig.get().announceSleepVote = savedEnable;
            MockPlayers.retire(sleeper);
            MockPlayers.retire(awakeC.player());
        };
        int[] step = {0};
        helper.onEachTick(() -> guarded(cleanup, () -> {
            switch (++step[0]) {
                case 2 -> {
                    sleeperC.channel().outboundMessages().clear();
                    sleepInBed(helper, sleeper);
                    helper.assertTrue(!chatKeys(sleeperC).contains(SleepVoteLines.ENTER_KEY),
                            "the feature off must whisper nothing; keys: " + chatKeys(sleeperC));
                    cleanup.run();
                    helper.succeed();
                }
                default -> {
                    if (step[0] >= 60) {
                        helper.fail("the disabled-path phase machine stalled");
                    }
                }
            }
        }));
    }

    /** Asserts the whisper's share args are exactly {@code sleeping} of {@code total}. */
    private static void assertCounts(GameTestHelper helper, TranslatableContents contents,
            int sleeping, int total) {
        Object[] args = contents.getArgs();
        helper.assertTrue(args.length == 3, "the whisper must carry name + two counts, got " + args.length);
        helper.assertTrue(Integer.valueOf(sleeping).equals(args[1]),
                "asleep count must be " + sleeping + ", got " + args[1]);
        helper.assertTrue(Integer.valueOf(total).equals(args[2]),
                "total count must be " + total + ", got " + args[2]);
    }
}
