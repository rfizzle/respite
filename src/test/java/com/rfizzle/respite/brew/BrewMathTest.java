// Tier: 1 (pure JUnit)
package com.rfizzle.respite.brew;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the brew's Haste-duration arithmetic ({@code design/SPEC.md} §6): the
 * default and both range extremes convert seconds to ticks, and a floor at zero
 * keeps a tampered value from producing a negative duration.
 */
class BrewMathTest {

    @Test
    void defaultNinetySecondsIsEighteenHundredTicks() {
        assertEquals(1800, BrewMath.hasteDurationTicks(90));
    }

    @Test
    void zeroSecondsGrantsNoHaste() {
        assertEquals(0, BrewMath.hasteDurationTicks(0));
    }

    @Test
    void maximumSixHundredSecondsIsTwelveThousandTicks() {
        assertEquals(12000, BrewMath.hasteDurationTicks(600));
    }

    @Test
    void negativeSecondsFloorToZero() {
        assertEquals(0, BrewMath.hasteDurationTicks(-5));
    }
}
