package com.rfizzle.respite.api;

import com.rfizzle.respite.chronometer.ChronometerTime;
import com.rfizzle.respite.registry.RespiteRegistry;
import com.rfizzle.respite.timelapse.LapseState;
import com.rfizzle.respite.timelapse.TimeLapseEngine;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.Level;

/**
 * Public, read-only API for Respite (Concord API Standard v1).
 *
 * <p>All methods are static accessors over Respite's server-side state. Nothing
 * here mutates that state; gameplay reads are authoritative on the server only.
 * The engines, handlers, and registries backing these accessors are internal —
 * consume this facade, never them. Respite has no HUD slot by design, so this
 * surface ships no HUD accessors.
 *
 * <p>Safe to use as a soft dependency: compile with {@code modCompileOnly}
 * and guard call sites with
 * {@code FabricLoader.getInstance().isModLoaded("respite")}.
 */
@Stable
public final class RespiteAPI {

    private RespiteAPI() {
    }

    /**
     * The effective time-lapse rate for a level — the acceleration actually
     * being applied this real tick (extras run + 1), not the target. The
     * time-lapse only ever runs in the Overworld, so any other dimension
     * always reads {@code 1}. Authoritative, server-side only.
     *
     * @param level the level
     * @return the effective rate, {@code 1} when the lapse is inactive or the
     *         level is not the Overworld
     */
    public static int getTimeLapseRate(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            return 1;
        }
        return TimeLapseEngine.getEffectiveRate();
    }

    /**
     * Whether the time-lapse is running extra ticks for a level right now. The
     * peril brake holding an otherwise-active lapse counts as <em>not</em>
     * active (no extras are running). Overworld-only, so any other dimension
     * always reads {@code false}. Authoritative, server-side only.
     *
     * @param level the level
     * @return true while extra ticks are being run for the Overworld
     */
    public static boolean isTimeLapseActive(ServerLevel level) {
        return level.dimension() == Level.OVERWORLD && TimeLapseEngine.getState() == LapseState.ACTIVE;
    }

    /**
     * A player's {@code TIME_SINCE_REST} statistic, in ticks — the count that
     * drives the Weariness ladder. Resets to 0 when the player starts sleeping,
     * dies, or drinks a Caffeinated Brew. Authoritative, server-side only.
     *
     * @param player the player
     * @return ticks since the player last rested
     */
    public static long getTicksSinceRest(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM, Stats.TIME_SINCE_REST);
    }

    /**
     * Whether a player currently carries the Weary status effect (the first
     * Weariness stage). Exactly one Weariness stage is active at a time, so a
     * Weary player is never also Exhausted. Authoritative, server-side only.
     *
     * @param player the player
     * @return true if the player currently has the Weary effect
     */
    public static boolean isWeary(ServerPlayer player) {
        return player.hasEffect(RespiteRegistry.WEARY);
    }

    /**
     * Whether a player currently carries the Exhausted status effect (the
     * second Weariness stage). Exactly one Weariness stage is active at a time,
     * so an Exhausted player is never also Weary. Authoritative, server-side
     * only.
     *
     * @param player the player
     * @return true if the player currently has the Exhausted effect
     */
    public static boolean isExhausted(ServerPlayer player) {
        return player.hasEffect(RespiteRegistry.EXHAUSTED);
    }

    /**
     * The Chronometer redstone signal (1–15) for a level's current day time,
     * the same pure function of time the Chronometer block emits. Fixed-time
     * dimensions (the Nether, the End) read {@code 0}. Authoritative,
     * server-side only.
     *
     * @param level the level
     * @return the signal 1–15, or 0 in a fixed-time dimension
     */
    public static int getChronometerSignal(Level level) {
        return ChronometerTime.signalFor(level.getDayTime(), level.dimensionType().hasFixedTime());
    }
}
