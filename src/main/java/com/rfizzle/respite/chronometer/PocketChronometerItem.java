package com.rfizzle.respite.chronometer;

import com.rfizzle.respite.registry.RespiteRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The pocket chronometer item ({@code design/SPEC.md} §5). Its whole surface is a
 * hover tooltip (assembled client-side in {@code PocketChronometerTooltip}); the
 * only server work is carrying the holder's days awake onto the stack, because
 * {@code TIME_SINCE_REST} is server-only state a client tooltip cannot read.
 *
 * <p>The carry is written on a 20-tick re-check grid and only when the displayed
 * figure changes — the block's "re-check grid, write on change" discipline — so a
 * held pocket chronometer never resyncs its stack every tick.
 */
public class PocketChronometerItem extends Item {

    /** Re-check grid for the carried days-awake read (SPEC §5's 20-tick cadence). */
    private static final int REFRESH_INTERVAL_TICKS = 20;

    public PocketChronometerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % REFRESH_INTERVAL_TICKS != 0) {
            return;
        }
        refreshAwakeTicks(stack, player);
    }

    /**
     * Refresh the stack's carried days-awake from the player's rest clock. The seam
     * the gametest drives directly, since mock players are not tick-driven.
     */
    public static void refreshAwakeTicks(ItemStack stack, ServerPlayer player) {
        long current = player.getStats().getValue(Stats.CUSTOM, Stats.TIME_SINCE_REST);
        int stored = stack.getOrDefault(RespiteRegistry.AWAKE_TICKS, 0);
        if (PocketChronometer.refreshDue(stored, current)) {
            stack.set(RespiteRegistry.AWAKE_TICKS, (int) current);
        }
    }
}
