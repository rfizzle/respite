package com.rfizzle.respite.brew;

/**
 * The Caffeinated Brew's one piece of pure arithmetic ({@code design/SPEC.md}
 * §6): the configured Haste duration in seconds converted to effect ticks. Kept
 * off the item so it unit-tests without a live entity — the seam
 * {@code CaffeinatedBrewItem} reads when it grants Haste.
 */
public final class BrewMath {

    /** Ticks per second — the world runs at 20 Hz. */
    static final int TICKS_PER_SECOND = 20;

    private BrewMath() {
    }

    /**
     * Haste duration in ticks for a configured {@code brewHasteSeconds}. The
     * config field is already clamped to {@code 0–600}; a floor at zero keeps a
     * hand-tampered value from ever asking for a negative duration.
     */
    public static int hasteDurationTicks(int brewHasteSeconds) {
        return Math.max(0, brewHasteSeconds) * TICKS_PER_SECOND;
    }
}
