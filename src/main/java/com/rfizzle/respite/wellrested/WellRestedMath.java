package com.rfizzle.respite.wellrested;

/**
 * The Well-Rested arithmetic ({@code design/SPEC.md} §4): the grace duration in
 * ticks and the per-effect natural-regen bonus. One formula, one home — the
 * grant, the regen mixin, and the tests all read from here.
 *
 * <p>Deliberately free of {@code net.minecraft} types so the duration and bonus
 * unit-test without a Fabric bootstrap, mirroring {@code WearinessMath}.
 */
public final class WellRestedMath {

    /** Vanilla ticks per second — the grace is configured in whole seconds (§4). */
    public static final int TICKS_PER_SECOND = 20;

    private WellRestedMath() {
    }

    /** The grace duration in ticks for a whole-second config value (§4). */
    public static int durationTicks(int wellRestedSeconds) {
        return wellRestedSeconds * TICKS_PER_SECOND;
    }

    /**
     * The multiplier a natural-regen heal is scaled by while Well-Rested:
     * {@code 1 + bonus} when the effect is present, {@code 1.0} otherwise (§4).
     * The bonus is the config fraction; the caller supplies the active value.
     * Composes multiplicatively with the weariness penalty in the regen mixin,
     * so the two never silently drop each other when both are present.
     */
    public static float regenFactor(boolean present, double wellRestedRegenBonus) {
        return present ? (float) (1.0 + wellRestedRegenBonus) : 1.0f;
    }
}
