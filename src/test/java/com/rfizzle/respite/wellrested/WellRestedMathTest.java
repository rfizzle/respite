// Tier: 1 (pure JUnit)
package com.rfizzle.respite.wellrested;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Well-Rested grace duration and regen bonus ({@code design/SPEC.md} §4): the
 * seconds→ticks conversion and the {@code 1 + bonus} multiplier, present and
 * absent. Pure — no Fabric bootstrap.
 */
class WellRestedMathTest {

    @Test
    void durationConvertsSecondsToTicks() {
        assertEquals(2400, WellRestedMath.durationTicks(120));
        assertEquals(0, WellRestedMath.durationTicks(0));
        assertEquals(12_000, WellRestedMath.durationTicks(600));
    }

    @Test
    void regenFactorIsOneWhenTheEffectIsAbsent() {
        assertEquals(1.0f, WellRestedMath.regenFactor(false, 0.5));
        assertEquals(1.0f, WellRestedMath.regenFactor(false, 2.0));
    }

    @Test
    void regenFactorAddsTheBonusWhenPresent() {
        assertEquals(1.5f, WellRestedMath.regenFactor(true, 0.5), 1.0e-6f);
        assertEquals(1.0f, WellRestedMath.regenFactor(true, 0.0), 1.0e-6f);
        assertEquals(3.0f, WellRestedMath.regenFactor(true, 2.0), 1.0e-6f);
    }
}
