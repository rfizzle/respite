package com.rfizzle.respite.block;

import com.rfizzle.respite.chronometer.ChronometerLines;
import com.rfizzle.respite.chronometer.ChronometerTime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
 */
public class ChronometerBlock extends Block {

    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    /** Ticks between level re-checks — at most one neighbor update per 1,600 world ticks. */
    private static final int RECHECK_INTERVAL_TICKS = 20;

    public ChronometerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
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

    /** A comparator reads the same 1–15 value the wires see. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(POWER);
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
        level.scheduleTick(pos, this, RECHECK_INTERVAL_TICKS);
    }

    /**
     * Right-click inspects: the action-bar clock/signal line on the server, a
     * quiet UI click on the client. No item consumed, no GUI.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level.isClientSide) {
            level.playLocalSound(pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.3f, 1.0f, false);
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(ChronometerLines.build("notification.respite.chronometer", level), true);
        }
        return InteractionResult.CONSUME;
    }

    private static int currentSignal(Level level) {
        return ChronometerTime.signalFor(level.getDayTime(), level.dimensionType().hasFixedTime());
    }
}
