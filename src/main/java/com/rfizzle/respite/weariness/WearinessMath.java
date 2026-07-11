package com.rfizzle.respite.weariness;

/**
 * The weariness arithmetic ({@code design/SPEC.md} §4): the threshold ladder
 * that turns a {@code TIME_SINCE_REST} tick count into a {@link WearinessStage},
 * and the per-stage natural-regen penalty. One formula, one home — the sweep,
 * the regen mixin, and the tests all read from here.
 *
 * <p>Deliberately free of {@code net.minecraft} types so the ladder and penalty
 * unit-test without a Fabric bootstrap.
 */
public final class WearinessMath {

    /** In-game ticks in one full day — the unit both thresholds are stated in (§4.1). */
    public static final long TICKS_PER_DAY = 24_000L;

    private WearinessMath() {
    }

    /**
     * The active stage for a player's {@code TIME_SINCE_REST}, given the two
     * day-thresholds (§4.1). Exhausted takes precedence over Weary — the config's
     * {@code exhausted ≥ weary + 1} clamp guarantees the exhausted line sits
     * strictly above the weary line, so the stages never collide.
     */
    public static WearinessStage stageFor(long ticksSinceRest, int wearinessThresholdDays, int exhaustedThresholdDays) {
        if (ticksSinceRest >= exhaustedThresholdDays * TICKS_PER_DAY) {
            return WearinessStage.EXHAUSTED;
        }
        if (ticksSinceRest >= wearinessThresholdDays * TICKS_PER_DAY) {
            return WearinessStage.WEARY;
        }
        return WearinessStage.NONE;
    }

    /**
     * The multiplier a natural-regen heal is scaled by for the given stage:
     * {@code 1 − penalty} while Weary or Exhausted, {@code 1.0} when rested (§4.3).
     * The penalties are the config fractions; the caller supplies the active
     * config's values.
     */
    public static float regenFactor(WearinessStage stage, double wearinessRegenPenalty, double exhaustedRegenPenalty) {
        return switch (stage) {
            case WEARY -> (float) (1.0 - wearinessRegenPenalty);
            case EXHAUSTED -> (float) (1.0 - exhaustedRegenPenalty);
            case NONE -> 1.0f;
        };
    }
}
