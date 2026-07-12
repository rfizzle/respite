package com.rfizzle.respite.bedroll;

import com.rfizzle.respite.config.RespiteConfig;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/**
 * The bedroll item ({@code design/SPEC.md} §7). Auto-use: a standing
 * right-click on the ground unrolls the bedroll <em>and</em> starts sleep in
 * one action, then it rolls back into the inventory on wake ({@link Bedroll}).
 * Crouch to place the block without sleeping (then right-click it to rest).
 *
 * <p>Placing consumes the item and breaking (or the dawn roll-up) returns it,
 * so the bedroll is conserved and endlessly reusable. When
 * {@code enableBedroll} is off the item is inert — it never places or sleeps —
 * so it can't be crafted or deployed, and nothing bricks (the recipe is gated
 * separately).
 */
public class BedrollItem extends BlockItem {

    public BedrollItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!RespiteConfig.get().enableBedroll) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        // Crouch = place only; the block's own right-click then handles resting.
        if (player == null || player.isSecondaryUseActive()) {
            return super.useOn(context);
        }
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        // Dry-run the bed rules at the target before committing the placement, so a
        // refusal (day, monsters near, wrong dimension, obstructed) never litters a
        // block. The target is computed once, before placement: recomputing it after
        // super.useOn() would shift by one on replaceable ground (the placed bedroll
        // is no longer replaceable), stranding the sleep in the air above.
        BlockPos target = new BlockPlaceContext(context).getClickedPos();
        Optional<Player.BedSleepingProblem> problem = Bedroll.sleepProblem(serverPlayer, target);
        if (problem.isPresent()) {
            Bedroll.sendProblem(serverPlayer, problem.get());
            return InteractionResult.FAIL;
        }
        InteractionResult placed = super.useOn(context);
        if (!placed.consumesAction()) {
            return placed;
        }
        // Rules already checked above; enterSleep verifies the bedroll landed here.
        Bedroll.enterSleep(serverPlayer, target);
        return placed;
    }
}
