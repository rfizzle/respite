package com.rfizzle.respite.bedroll;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The bedroll ({@code design/SPEC.md} §7) — a one-tile camp bed for the road.
 * It extends {@link BedBlock} deliberately: being a genuine bed is what lets
 * vanilla's per-tick sleeper-eject ({@code LivingEntity#checkBedExists}) and
 * the client "Leave Bed" overlay both work with no mixin. Respite changes only
 * what a bedroll's sleep <em>does</em> — never sets spawn, heals at half
 * strength (§2) — routing every sleep through {@link Bedroll#sleep}, which
 * replicates the vanilla bed rules minus the respawn-point set.
 *
 * <p>Single-tile, unlike a vanilla two-block bed: {@link #setPlacedBy} never
 * lays a head half, {@link #updateShape} never self-destructs for a missing
 * head, and {@link #getStateForPlacement} needs no head space. The low slab
 * shape reads as a rolled mat on the ground.
 */
public class BedrollBlock extends BedBlock {

    /** A flat mat on the floor — 4px tall, full footprint. */
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 4.0, 16.0);

    public BedrollBlock(DyeColor color, Properties properties) {
        super(color, properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * A vanilla bed renders its cloth through a {@code BedBlockEntity} + block-
     * entity renderer; the bedroll ships a plain static model instead, so it
     * renders as an ordinary model and creates no block entity — which also
     * avoids the {@code minecraft:bed} block-entity-type validation rejecting a
     * bedroll state.
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return null;
    }

    /** One tile: place with the player's facing, and never demand a head space. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    /** One tile: skip vanilla's head-half placement entirely. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        // Intentionally not calling super: BedBlock#setPlacedBy lays the head half,
        // which a single-tile bedroll must not have.
    }

    /** One tile: no head half to sync with, so a neighbor change never removes it. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state;
    }

    /**
     * Right-click to rest — Respite's spawn-suppressing sleep, never vanilla's
     * spawn-setting/nether-exploding bed path. A placed bedroll keeps working
     * even with the feature toggled off afterwards (its recipe is gone, but an
     * already-placed block never bricks), mirroring the Chronometer (§5).
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            Bedroll.sleep(serverPlayer, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
