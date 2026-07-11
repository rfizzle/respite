// Tier: 1 (pure JUnit)
package com.rfizzle.respite.weariness;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The weariness ladder and penalty ({@code design/SPEC.md} §4.1, §4.3): threshold
 * crossings in both directions, Exhausted's precedence over Weary at the second
 * line, and the per-stage regen multiplier. Pure — no Fabric bootstrap.
 */
class WearinessMathTest {

    private static final int WEARY_DAYS = 3;
    private static final int EXHAUSTED_DAYS = 6;

    @Test
    void belowTheWearyLineIsRested() {
        assertEquals(WearinessStage.NONE, stage(0));
        assertEquals(WearinessStage.NONE, stage(3 * 24_000 - 1));
    }

    @Test
    void theWearyLineIsInclusive() {
        assertEquals(WearinessStage.WEARY, stage(3 * 24_000));
        assertEquals(WearinessStage.WEARY, stage(3 * 24_000 + 1));
    }

    @Test
    void wearyHoldsUntilTheExhaustedLine() {
        assertEquals(WearinessStage.WEARY, stage(6 * 24_000 - 1));
    }

    @Test
    void theExhaustedLineIsInclusiveAndTakesPrecedence() {
        assertEquals(WearinessStage.EXHAUSTED, stage(6 * 24_000));
        assertEquals(WearinessStage.EXHAUSTED, stage(100 * 24_000));
    }

    @Test
    void regenFactorIsOneWhenRested() {
        assertEquals(1.0f, WearinessMath.regenFactor(WearinessStage.NONE, 0.25, 0.50));
    }

    @Test
    void regenFactorSubtractsTheStagePenalty() {
        assertEquals(0.75f, WearinessMath.regenFactor(WearinessStage.WEARY, 0.25, 0.50), 1.0e-6f);
        assertEquals(0.50f, WearinessMath.regenFactor(WearinessStage.EXHAUSTED, 0.25, 0.50), 1.0e-6f);
    }

    @Test
    void regenFactorTracksNonDefaultPenalties() {
        assertEquals(0.10f, WearinessMath.regenFactor(WearinessStage.WEARY, 0.90, 0.95), 1.0e-6f);
        assertEquals(0.05f, WearinessMath.regenFactor(WearinessStage.EXHAUSTED, 0.90, 0.95), 1.0e-6f);
    }

    private static WearinessStage stage(long ticks) {
        return WearinessMath.stageFor(ticks, WEARY_DAYS, EXHAUSTED_DAYS);
    }
}
