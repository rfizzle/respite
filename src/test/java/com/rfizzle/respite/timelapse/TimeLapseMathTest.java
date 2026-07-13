// Tier: 1 (pure JUnit)
package com.rfizzle.respite.timelapse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the rate formula to {@code design/SPEC.md} §1:
 * {@code max(1, round(maxTimeLapseRate × k / n))} across the full k/n sweep,
 * including the inactive edges and rounding at every k for n = 4.
 */
class TimeLapseMathTest {

    @ParameterizedTest
    @CsvSource({
            // the spec's own worked example: default 60, n = 4
            "60, 0, 4, 1",
            "60, 1, 4, 15",
            "60, 2, 4, 30",
            "60, 3, 4, 45",
            "60, 4, 4, 60",
    })
    void specExampleSweep(int maxRate, int sleeping, int total, int expected) {
        assertEquals(expected, TimeLapseMath.targetRate(maxRate, sleeping, total));
    }

    @ParameterizedTest
    @CsvSource({
            // a solo player gets the full rate
            "60, 1, 1, 60",
            // no sleepers or no players → rate 1, time-lapse inactive
            "60, 0, 1, 1",
            "60, 0, 0, 1",
            // defensive: nonsensical negatives behave like the empty cases
            "60, -1, 4, 1",
            "60, 1, -1, 1",
    })
    void inactiveAndSoloEdges(int maxRate, int sleeping, int total, int expected) {
        assertEquals(expected, TimeLapseMath.targetRate(maxRate, sleeping, total));
    }

    @ParameterizedTest
    @CsvSource({
            // round() at non-quarter shares: 60 × 1/7 = 8.57… → 9; 60 × 2/7 = 17.1… → 17
            "60, 1, 7, 9",
            "60, 2, 7, 17",
            // half-up rounding: 3 × 1/2 = 1.5 → 2
            "3, 1, 2, 2",
            // a tiny share never drops below 1
            "2, 1, 100, 1",
    })
    void roundingAndFloor(int maxRate, int sleeping, int total, int expected) {
        assertEquals(expected, TimeLapseMath.targetRate(maxRate, sleeping, total));
    }

    @ParameterizedTest
    @CsvSource({
            // config range extremes (2–100) with everyone asleep
            "2, 4, 4, 2",
            "100, 4, 4, 100",
            "100, 1, 4, 25",
            "2, 1, 4, 1",       // 2 × 1/4 = 0.5 → round 1 (floor holds anyway)
    })
    void configRangeExtremes(int maxRate, int sleeping, int total, int expected) {
        assertEquals(expected, TimeLapseMath.targetRate(maxRate, sleeping, total));
    }

    @ParameterizedTest
    @CsvSource({"1", "2", "3", "4", "5", "6", "7", "8"})
    void fullHouseAlwaysHitsTheMax(int players) {
        assertEquals(60, TimeLapseMath.targetRate(60, players, players));
    }

    @ParameterizedTest
    @CsvSource({
            // enabled, now, lastAction, thresholdMinutes, expectedIdle
            // the 5-minute (300_000 ms) boundary: at the threshold is idle
            "true, 300000, 0, 5, true",
            "true, 299999, 0, 5, false",
            "true, 300001, 0, 5, true",
            // the 1-minute (60_000 ms) boundary
            "true, 60000, 0, 1, true",
            "true, 59999, 0, 1, false",
            // freshly active (no gap) and clock-skew (last in the future) never read as idle
            "true, 1000, 1000, 5, false",
            "true, 500, 1000, 5, false",
            // disabled exclusion → never idle, however large the gap
            "false, 100000000, 0, 5, false",
            // a non-positive threshold → never idle (defensive; clamp keeps it ≥ 1 in practice)
            "true, 100000000, 0, 0, false",
            "true, 100000000, 0, -1, false",
            // realistic millis magnitudes, well past the 32-bit range
            "true, 1700000600000, 1700000000000, 5, true",
            "true, 1700000200000, 1700000000000, 5, false",
    })
    void isIdleTruthTable(boolean enabled, long now, long lastAction, int thresholdMinutes, boolean expected) {
        assertEquals(expected, TimeLapseMath.isIdle(enabled, now, lastAction, thresholdMinutes));
    }
}
