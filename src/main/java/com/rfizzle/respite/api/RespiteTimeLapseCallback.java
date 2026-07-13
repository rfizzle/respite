package com.rfizzle.respite.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerLevel;

/**
 * Callback fired when the effective time-lapse rate changes. Part of Respite's
 * stable API surface (Concord API Standard v1).
 *
 * <p>Fired <strong>server-side only</strong>, once per change, from the
 * Overworld's per-real-tick rate evaluation. The <em>effective</em> rate is
 * reported — the acceleration actually applied under the performance governor,
 * not the target. It fires on every crossing, including the start of a lapse
 * (old rate 1) and its end (new rate 1); the peril brake holding an active
 * lapse settles the rate to 1 and so fires an end here. It does <em>not</em>
 * fire when the sleeper count changes without moving the rounded rate, and it
 * fires regardless of the {@code announceTimeLapse} config toggle (that toggle
 * gates the player-facing action bar, not this API event).
 *
 * <p>A listener that throws is not isolated by Respite; keep listener bodies
 * cheap and exception-free — this fires on the server tick loop's hot path.
 */
@Stable
public interface RespiteTimeLapseCallback {

    Event<RespiteTimeLapseCallback> EVENT = EventFactory.createArrayBacked(RespiteTimeLapseCallback.class,
            listeners -> (level, oldRate, newRate, sleeping, total) -> {
                for (RespiteTimeLapseCallback listener : listeners) {
                    listener.onRateChanged(level, oldRate, newRate, sleeping, total);
                }
            });

    /**
     * Called after the effective time-lapse rate has changed.
     *
     * @param level    the Overworld the lapse runs in
     * @param oldRate  the effective rate before this change
     * @param newRate  the effective rate after this change
     * @param sleeping active (non-spectator, non-idle) Overworld players in bed
     *                 at this evaluation — the {@code k} the rate is computed over
     * @param total    active (non-spectator, non-idle) Overworld players at this
     *                 evaluation — the {@code n} the rate is computed over, not the
     *                 full online roster (an idle player counts for nothing; SPEC §1)
     */
    void onRateChanged(ServerLevel level, int oldRate, int newRate, int sleeping, int total);
}
