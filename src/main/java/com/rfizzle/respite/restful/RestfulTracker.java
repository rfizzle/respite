package com.rfizzle.respite.restful;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The armed sleepers ({@code design/SPEC.md} §2): player UUID → their night's
 * {@link RestState}. Only armed players are tracked — an unarmed sleeper costs
 * one map miss per tick and nothing else.
 *
 * <p>Deliberately transient, per the spec's persistence rule: the armed flag
 * and interval counter are never written to the player's saved data, and a
 * server stopping mid-sleep loses at most one partial interval. Server thread
 * only; the per-tick read path allocates nothing (one {@code HashMap.get} per
 * sleeping player). Entries drop on wake and disconnect, and are bounded by
 * the online player count in between.
 */
public final class RestfulTracker {

    private final Map<UUID, RestState> sleepers = new HashMap<>();

    /** The player started sleeping armed — begin their night's accounting. */
    public void arm(UUID playerId) {
        arm(playerId, false);
    }

    /** As {@link #arm(UUID)}, recording whether the night is slept in a bedroll (§7). */
    public void arm(UUID playerId, boolean bedroll) {
        sleepers.put(playerId, new RestState(bedroll));
    }

    /** The player's live night, or null when they aren't an armed sleeper. */
    public RestState get(UUID playerId) {
        return sleepers.get(playerId);
    }

    /** The night ended (wake or disconnect) — drop and hand back its tally. */
    public RestState forget(UUID playerId) {
        return sleepers.remove(playerId);
    }

    /** Full reset — server stopped. */
    public void clear() {
        sleepers.clear();
    }

    /** True when nobody is armed. */
    public boolean isEmpty() {
        return sleepers.isEmpty();
    }
}
