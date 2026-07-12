package com.rfizzle.respite.bedroll;

import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The bedroll's sleep machinery ({@code design/SPEC.md} §7). Sleeping in a
 * bedroll is a genuine vanilla sleep — so it counts toward the time-lapse
 * share (§1), arms Restful Saturation (§2), and clears Weariness (§4) for free
 * — with two Respite twists routed through here:
 *
 * <ul>
 *   <li>{@link #trySleep} replicates the vanilla bed-sleep rules (night-only,
 *       monsters-near, obstruction, natural dimension) but omits the one
 *       {@code setRespawnPosition} call, so a bedroll <em>never sets spawn</em>
 *       — no mixin needed. It is the road's shelter, not a home.</li>
 *   <li>The bedroll rolls back into the sleeper's inventory on any wake from it
 *       (dawn, damage, leaving the bed) via {@link #onStopSleeping}; a
 *       disconnect or server stop mid-sleep leaves the block placed, reclaimed
 *       by breaking it.</li>
 * </ul>
 *
 * <p>All state lives in the placed block itself — no transient tracker to leak.
 * Server-thread only.
 */
public final class Bedroll {

    /** Horizontal/vertical radius of the vanilla "monsters nearby" rest check. */
    private static final double MONSTER_RADIUS_XZ = 8.0;
    private static final double MONSTER_RADIUS_Y = 5.0;

    private Bedroll() {
    }

    public static void register() {
        // The screen-open nudge (S2C) — the type must be known on both sides.
        PayloadTypeRegistry.playS2C().register(BedrollSleepPayload.TYPE, BedrollSleepPayload.CODEC);
        // Roll the bedroll back up whenever its sleeper wakes.
        EntitySleepEvents.STOP_SLEEPING.register(Bedroll::onStopSleeping);
    }

    /**
     * Start of a bedroll sleep, if the bed rules allow it. Sends the waking
     * player the vanilla problem message on a soft refusal; on success the
     * player is asleep with spawn untouched. Called both from the block's
     * right-click and the item's auto-use.
     */
    public static void sleep(ServerPlayer player, BlockPos pos) {
        Optional<Player.BedSleepingProblem> problem = sleepProblem(player, pos);
        if (problem.isPresent()) {
            sendProblem(player, problem.get());
            return;
        }
        // Spawn-suppressing sleep: vanilla ServerPlayer#startSleepInBed minus the
        // setRespawnPosition call. startSleeping resets TIME_SINCE_REST (§4 clear)
        // and sets the sleeping pose, so the time-lapse counts the sleeper (§1) and
        // Fabric's START_SLEEPING fires to arm Restful Saturation (§2).
        player.startSleeping(pos);
        player.awardStat(Stats.SLEEP_IN_BED);
        CriteriaTriggers.SLEPT_IN_BED.trigger(player);
        ((ServerLevel) player.level()).updateSleepingPlayerList();
        // Open the client's Leave-Bed overlay for this server-initiated sleep.
        // Guarded so a client without the receiver (or a gametest mock) is a no-op.
        if (ServerPlayNetworking.canSend(player, BedrollSleepPayload.TYPE)) {
            ServerPlayNetworking.send(player, BedrollSleepPayload.INSTANCE);
        }
    }

    /**
     * The bed rules that gate a bedroll sleep, evaluated without mutating
     * anything (so the item can dry-run before committing a placement). Mirrors
     * the checks in vanilla {@code ServerPlayer#startSleepInBed}, minus spawn,
     * bed-range (the sleeper is always at the bedroll), and the head half.
     *
     * @return the problem, or empty when sleep may begin.
     */
    public static Optional<Player.BedSleepingProblem> sleepProblem(ServerPlayer player, BlockPos pos) {
        if (player.isSleeping() || !player.isAlive()) {
            return Optional.of(Player.BedSleepingProblem.OTHER_PROBLEM);
        }
        Level level = player.level();
        if (!level.dimensionType().natural()) {
            return Optional.of(Player.BedSleepingProblem.NOT_POSSIBLE_HERE);
        }
        if (level.isDay()) {
            return Optional.of(Player.BedSleepingProblem.NOT_POSSIBLE_NOW);
        }
        if (!player.isCreative()) {
            Vec3 center = Vec3.atBottomCenterOf(pos);
            AABB box = new AABB(
                    center.x() - MONSTER_RADIUS_XZ, center.y() - MONSTER_RADIUS_Y, center.z() - MONSTER_RADIUS_XZ,
                    center.x() + MONSTER_RADIUS_XZ, center.y() + MONSTER_RADIUS_Y, center.z() + MONSTER_RADIUS_XZ);
            List<Monster> nearby = level.getEntitiesOfClass(Monster.class, box,
                    monster -> monster.isPreventingPlayerRest(player));
            if (!nearby.isEmpty()) {
                return Optional.of(Player.BedSleepingProblem.NOT_SAFE);
            }
        }
        return Optional.empty();
    }

    /** Localized feedback for a soft sleep refusal, on the action bar like vanilla. */
    public static void sendProblem(ServerPlayer player, Player.BedSleepingProblem problem) {
        Component message = problem.getMessage();
        if (message != null) {
            player.displayClientMessage(message, true);
        }
    }

    /**
     * Roll the bedroll back into the waking sleeper's inventory. Fires on every
     * wake path Fabric reports (dawn, damage, leaving the bed) but not on
     * disconnect — a bedroll left behind by a disconnect stays a placed block,
     * reclaimed by breaking it, so nothing is ever duplicated or lost.
     */
    private static void onStopSleeping(LivingEntity entity, BlockPos sleepingPos) {
        if (!(entity instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        Level level = player.level();
        if (!(level.getBlockState(sleepingPos).getBlock() instanceof BedrollBlock bedroll)) {
            return;
        }
        // Remove without a drop — the item is handed straight back below, so the
        // world never holds both the block and a dropped item at once.
        level.removeBlock(sleepingPos, false);
        ItemStack stack = new ItemStack(bedroll);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
