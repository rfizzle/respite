package com.rfizzle.respite.weariness;

/**
 * The Exhausted eyelid-blink timing ({@code design/SPEC.md} §4.4): the jitter
 * schedule, the combat-suppression window, and the ease curve. Pure and
 * client-agnostic — no {@code net.minecraft} or render types — so the whole
 * cadence unit-tests without a client bootstrap. All durations are client ticks
 * (20/s); {@link com.rfizzle.respite.client.WearinessBlinkHandler} owns the live
 * state and the draw.
 */
public final class BlinkMath {

    /** Interval floor — 90s − 30s jitter (§4.4). */
    public static final int MIN_INTERVAL_TICKS = 1200;
    /** Interval ceiling — 90s + 30s jitter (§4.4). */
    public static final int MAX_INTERVAL_TICKS = 2400;

    /** Eyelids ease in over ~0.3s (§4.4). */
    public static final int EASE_IN_TICKS = 6;
    /** …and release over ~0.3s. */
    public static final int EASE_OUT_TICKS = 6;
    /** One blink from first droop to fully open. */
    public static final int BLINK_DURATION_TICKS = EASE_IN_TICKS + EASE_OUT_TICKS;

    /** Peak occlusion — 55%, never full black, vision never lost (§4.4). */
    public static final float PEAK_OCCLUSION = 0.55f;

    /** No blink begins within 200 ticks (10s) of taking or dealing damage (§4.4). */
    public static final int COMBAT_SUPPRESS_TICKS = 200;

    private BlinkMath() {
    }

    /**
     * Ticks until the next blink, drawn from {@code [MIN, MAX]} by a uniform
     * {@code random} in {@code [0, 1)} — 90s ± 30s of jitter. Bounds are inclusive
     * at the floor and reach the ceiling as {@code random → 1}.
     */
    public static int nextInterval(double random) {
        int span = MAX_INTERVAL_TICKS - MIN_INTERVAL_TICKS;
        return MIN_INTERVAL_TICKS + (int) Math.round(random * span);
    }

    /**
     * Whether a blink may begin now — true once the combat window has cleared.
     * {@code lastCombatTick < 0} means no combat has been observed this session.
     * A due blink held here is deferred, not skipped: it fires the first tick
     * this returns true.
     */
    public static boolean canBlink(long now, long lastCombatTick) {
        return lastCombatTick < 0 || now - lastCombatTick >= COMBAT_SUPPRESS_TICKS;
    }

    /**
     * Whether a new blink should start this tick: one is scheduled and due, none
     * is already running, and combat isn't suppressing it. When due-but-suppressed
     * this returns false without consuming the schedule, so the blink waits out
     * the window and fires on the next clear tick.
     */
    public static boolean shouldStartBlink(long now, long nextBlinkTick, long lastCombatTick, boolean blinking) {
        return !blinking && now >= nextBlinkTick && canBlink(now, lastCombatTick);
    }

    /**
     * Occlusion fraction {@code elapsed} ticks into a blink: a triangle rising to
     * {@link #PEAK_OCCLUSION} at the midpoint and back to zero, clamped to
     * {@code [0, PEAK_OCCLUSION]}. Zero once the blink is done.
     */
    public static float occlusionAt(long elapsed) {
        if (elapsed < 0 || elapsed >= BLINK_DURATION_TICKS) {
            return 0.0f;
        }
        if (elapsed < EASE_IN_TICKS) {
            return PEAK_OCCLUSION * (elapsed + 1) / EASE_IN_TICKS;
        }
        long releaseElapsed = elapsed - EASE_IN_TICKS;
        return PEAK_OCCLUSION * (EASE_OUT_TICKS - releaseElapsed) / EASE_OUT_TICKS;
    }

    /** True once a blink that started {@code elapsed} ticks ago has fully opened. */
    public static boolean isBlinkDone(long elapsed) {
        return elapsed >= BLINK_DURATION_TICKS;
    }
}
