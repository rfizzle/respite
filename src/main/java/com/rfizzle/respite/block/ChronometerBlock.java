package com.rfizzle.respite.block;

import com.rfizzle.respite.chronometer.ChronometerLines;
import com.rfizzle.respite.chronometer.ChronometerTime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Chronometer ({@code design/SPEC.md} §5) — a copper-and-redstone timepiece
 * emitting power 1–15 as a pure function of the day time, with each level
 * spanning 1,600 ticks. Fixed-time dimensions read 0 and show the still face.
 *
 * <p>The current level is held in the {@code power} blockstate property (the
 * daylight-detector pattern): a self-rescheduled 20-tick block tick recomputes
 * {@link ChronometerTime#signalFor} and swaps the state only when the level
 * changed, so neighbor updates fire exactly on level change and the signal
 * reads straight off the state with no level access. The dial's 8 visual
 * phases (two levels per face, {@code power=0} the still face) are the
 * blockstate JSON's mapping of this property onto the dial models.
 *
 * <p>No block entity: the block holds no items and no state beyond the vanilla
 * blockstate, and vanilla persists both the property and the pending block
 * tick with the chunk. A tick that fires late (a chunk reloaded after a long
 * absence) self-corrects, because the formula reads the current day time
 * rather than accumulating.
 *
 * <p>A comparator reads {@linkplain ChronometerTime#moonFullness moon fullness}
 * (0–15, new moon dark to full moon bright), separate from the wire signal's
 * hour meaning. A per-block {@link #ALARM_HOUR} — set by sneak-right-click,
 * vanilla-persisted with the chunk, and unmentioned by the blockstate JSON so
 * it maps like a slab's waterlogged — chimes a vanilla bell once when its hour
 * arrives.
 */
public class ChronometerBlock extends Block {

    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    /** The set alarm hour, 0–23; {@link ChronometerTime#ALARM_OFF} (24) is "no alarm". */
    public static final IntegerProperty ALARM_HOUR = IntegerProperty.create("alarm_hour", 0, ChronometerTime.ALARM_OFF);

    /** Ticks between level re-checks — at most one neighbor update per 1,600 world ticks. */
    private static final int RECHECK_INTERVAL_TICKS = 20;

    public ChronometerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWER, 0).setValue(ALARM_HOUR, ChronometerTime.ALARM_OFF));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER, ALARM_HOUR);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /** Weak power to adjacent wires and components; never strong power through blocks. */
    @Override
    protected int getSignal(BlockState state, BlockGetter getter, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /**
     * A comparator reads moon fullness (0–15, new moon dark to full moon
     * bright) — distinct from the wire signal's hour meaning. Fixed-time
     * dimensions have no cycling moon, so they read 0.
     */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.dimensionType().hasFixedTime()) {
            return 0;
        }
        return ChronometerTime.moonFullness(level.getMoonPhase());
    }

    /** Player placement lands with the correct level already set. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(POWER, currentSignal(context.getLevel()));
    }

    /**
     * Any placement path (player, piston, command, structure) arms the tick
     * chain; the 1-tick delay also snaps a default-state placement that
     * bypassed {@link #getStateForPlacement} to the correct level.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !oldState.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int signal = currentSignal(level);
        if (state.getValue(POWER) != signal) {
            level.setBlock(pos, state.setValue(POWER, signal), Block.UPDATE_ALL);
        }
        chimeIfAlarmDue(state, level, pos);
        level.scheduleTick(pos, this, RECHECK_INTERVAL_TICKS);
    }

    /**
     * Rings the alarm once when its hour arrives. The re-check grid's period is
     * {@link #RECHECK_INTERVAL_TICKS}, so {@link ChronometerTime#alarmFires} lands
     * in each hour's window exactly once; the {@code doDaylightCycle} gate keeps a
     * frozen clock from re-chiming while parked in that window. Allocation-free
     * except on the firing tick, per the time-lapse hot-path budget.
     */
    private static void chimeIfAlarmDue(BlockState state, ServerLevel level, BlockPos pos) {
        int alarmHour = state.getValue(ALARM_HOUR);
        if (alarmHour != ChronometerTime.ALARM_OFF
                && level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)
                && ChronometerTime.alarmFires(level.getDayTime(), alarmHour, RECHECK_INTERVAL_TICKS)) {
            level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }

    /**
     * Right-click inspects; sneak-right-click cycles the alarm hour. Both give
     * an action-bar line on the server and a quiet UI click on the client. No
     * item consumed, no GUI.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level.isClientSide) {
            level.playLocalSound(pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.3f, 1.0f, false);
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            int next = ChronometerTime.cycleAlarm(state.getValue(ALARM_HOUR));
            // Alarm hour has no redstone or visual effect, so notify clients only — no neighbor updates.
            level.setBlock(pos, state.setValue(ALARM_HOUR, next), Block.UPDATE_CLIENTS);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(alarmSetLine(next), true);
            }
            return InteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    ChronometerLines.build("notification.respite.chronometer", level, state.getValue(ALARM_HOUR)), true);
        }
        return InteractionResult.CONSUME;
    }

    /** The sneak-cycle confirmation: the new alarm hour, or "off". */
    private static Component alarmSetLine(int alarmHour) {
        return alarmHour == ChronometerTime.ALARM_OFF
                ? Component.translatable("notification.respite.chronometer_alarm_off")
                : Component.translatable("notification.respite.chronometer_alarm_set",
                        ChronometerTime.hourLabel(alarmHour));
    }

    private static int currentSignal(Level level) {
        return ChronometerTime.signalFor(level.getDayTime(), level.dimensionType().hasFixedTime());
    }
}
