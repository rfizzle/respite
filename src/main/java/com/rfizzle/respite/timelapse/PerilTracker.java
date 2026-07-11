package com.rfizzle.respite.timelapse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Transient peril state for the brake ({@code design/SPEC.md} §1): which
 * hostile mobs currently target which player, and when each player last
 * dealt, took, or was menaced by damage — all on the <em>real</em>-tick
 * clock, so the window is never compressed by the lapse itself.
 *
 * <p>Deliberately not persisted (a restart forgets any live fight — the mobs'
 * aggro re-registers on their next {@code setTarget}) and deliberately free of
 * {@code net.minecraft} types: the mixin and events translate to UUIDs, so the
 * decay and transition logic unit-tests without a Fabric bootstrap. Server
 * thread only; the per-tick read path allocates nothing.
 */
public final class PerilTracker {

    /** Hostile mob → the player it currently targets. */
    private final Map<UUID, UUID> mobTargets = new HashMap<>();

    /** Player → number of hostile mobs currently targeting them. */
    private final Map<UUID, Integer> targetedCounts = new HashMap<>();

    /** Player → real tick of their last peril signal (damage, or targeted-while-queried). */
    private final Map<UUID, Long> lastPerilTick = new HashMap<>();

    /**
     * A hostile mob's target changed. {@code playerId} is null when the new
     * target is not a player (including cleared targets). Self-consistent even
     * when transitions were missed: the previous claim comes from this map,
     * never from the caller.
     */
    public void onMobTarget(UUID mobId, UUID playerId) {
        UUID previous = playerId == null ? mobTargets.remove(mobId) : mobTargets.put(mobId, playerId);
        if (previous != null && !previous.equals(playerId)) {
            release(previous);
        }
        if (playerId != null && !playerId.equals(previous)) {
            targetedCounts.merge(playerId, 1, Integer::sum);
        }
    }

    /** A tracked mob died, despawned, or unloaded — drop its claim. */
    public void onMobRemoved(UUID mobId) {
        UUID previous = mobTargets.remove(mobId);
        if (previous != null) {
            release(previous);
        }
    }

    /** The player dealt or took damage on this real tick. */
    public void recordCombat(UUID playerId, long realTick) {
        lastPerilTick.put(playerId, realTick);
    }

    /**
     * True while the player is in peril: a hostile currently targets them
     * (which also refreshes their peril tick, so release lands exactly
     * {@code windowTicks} real ticks after the last signal), or their last
     * peril signal is at most {@code windowTicks} real ticks old. Expired
     * entries self-prune on read.
     */
    public boolean isInPeril(UUID playerId, long nowRealTick, int windowTicks) {
        if (targetedCounts.getOrDefault(playerId, 0) > 0) {
            lastPerilTick.put(playerId, nowRealTick);
            return true;
        }
        Long last = lastPerilTick.get(playerId);
        if (last == null) {
            return false;
        }
        if (nowRealTick - last <= windowTicks) {
            return true;
        }
        lastPerilTick.remove(playerId);
        return false;
    }

    /**
     * A player disconnected. Their damage window drops; targeting counts stay
     * with the mobs that hold them and release through {@link #onMobTarget}
     * or {@link #onMobRemoved} as the mobs retarget or unload.
     */
    public void forgetPlayer(UUID playerId) {
        lastPerilTick.remove(playerId);
    }

    /** Full reset — server stopped, or the feature was toggled off. */
    public void clear() {
        mobTargets.clear();
        targetedCounts.clear();
        lastPerilTick.clear();
    }

    /** True when nothing is tracked — the disable sweep's cheap precheck. */
    public boolean isEmpty() {
        return mobTargets.isEmpty() && targetedCounts.isEmpty() && lastPerilTick.isEmpty();
    }

    private void release(UUID playerId) {
        targetedCounts.computeIfPresent(playerId, (id, count) -> count <= 1 ? null : count - 1);
    }
}
