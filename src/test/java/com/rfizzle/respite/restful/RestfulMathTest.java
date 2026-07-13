// Tier: 1 (pure JUnit)
package com.rfizzle.respite.restful;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The restful-saturation arithmetic ({@code design/SPEC.md} §2): the arming
 * gate at both strictness levels, the per-step stop conditions, the Deep
 * Sleep lunar gradient at default and range extremes, the wake-line choice,
 * and full-night accounting — 20 intervals across a 12,000-tick night, the
 * 10-saturation Deep Sleep full heal, and both stop conditions mid-night.
 */
class RestfulMathTest {

    private static final float EPS = 1e-6f;

    // --- Arming gate (§2.1) ---

    @Test
    void strictGateRequiresTheFullBar() {
        assertTrue(RestfulMath.arms(20, true));
        assertFalse(RestfulMath.arms(19, true));
        assertFalse(RestfulMath.arms(0, true));
    }

    @Test
    void relaxedGateArmsAtVanillasRegenThreshold() {
        assertTrue(RestfulMath.arms(20, false));
        assertTrue(RestfulMath.arms(18, false));
        assertFalse(RestfulMath.arms(17, false));
    }

    // --- Per-step stop conditions (§2.6) ---

    @Test
    void stepRunsOnlyAboveTheSaturationFloor() {
        assertTrue(RestfulMath.stepAllowed(1.0f, 10.0f, 20.0f));
        assertFalse(RestfulMath.stepAllowed(0.99f, 10.0f, 20.0f));
        assertFalse(RestfulMath.stepAllowed(0.0f, 10.0f, 20.0f));
    }

    @Test
    void stepStopsAtFullHealthButNotBefore() {
        assertFalse(RestfulMath.stepAllowed(5.0f, 20.0f, 20.0f));
        assertTrue(RestfulMath.stepAllowed(5.0f, 19.5f, 20.0f));
    }

    // --- Deep Sleep lunar gradient (§2.3) ---

    @Test
    void newMoonHealsAtTheFullPeak() {
        assertEquals(2.0f, RestfulMath.healPerStep(RestfulMath.NEW_MOON_PHASE, 2.0), EPS);
    }

    @Test
    void fullMoonHealsTheBaseFloorNoBonus() {
        assertEquals(1.0f, RestfulMath.healPerStep(RestfulMath.FULL_MOON_PHASE, 2.0), EPS);
    }

    @Test
    void healRampsLinearlyAcrossThePhasesAtTheDefaultPeak() {
        // phases 0..7 (full, waning gibbous, third quarter, waning crescent,
        // new, waxing crescent, first quarter, waxing gibbous): the darkness
        // ramp at the default peak 2.0 — floor 1.0 at the full moon, +0.25 per
        // phase toward the new moon, symmetric about it.
        float[] expected = {1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 1.75f, 1.5f, 1.25f};
        for (int phase = 0; phase < 8; phase++) {
            assertEquals(expected[phase], RestfulMath.healPerStep(phase, 2.0), EPS,
                    "phase " + phase + " heal on the default gradient");
        }
    }

    @Test
    void deepSleepMultiplierIsTheGradientDirectly() {
        // the pure multiplier: 1 at the full moon, the peak at the new moon,
        // stepping evenly between — the same ramp healPerStep scales the base by.
        double[] expected = {1.0, 1.25, 1.5, 1.75, 2.0, 1.75, 1.5, 1.25};
        for (int phase = 0; phase < 8; phase++) {
            assertEquals(expected[phase], RestfulMath.deepSleepMultiplier(phase, 2.0), EPS,
                    "phase " + phase + " multiplier on the default gradient");
        }
    }

    @Test
    void gradientCollapsesToTheFloorWhenThePeakIsOne() {
        // peak 1.0 (the low range extreme) — no bonus at any phase
        for (int phase = 0; phase < 8; phase++) {
            assertEquals(1.0f, RestfulMath.healPerStep(phase, 1.0), EPS,
                    "phase " + phase + " must heal the floor when the peak is 1.0");
        }
    }

    @Test
    void multiplierRangeExtremesRampFromTheFullMoonToThePeak() {
        // the SPEC's "range extremes" (§2.3): the new moon still hits the peak,
        // the full moon still floors at 1.0, and a mid-phase interpolates. At the
        // high extreme 4.0 the quarter moon (phase 2) heals 1 + 3×0.5 = 2.5.
        assertEquals(1.0f, RestfulMath.healPerStep(RestfulMath.NEW_MOON_PHASE, 1.0), EPS);
        assertEquals(4.0f, RestfulMath.healPerStep(RestfulMath.NEW_MOON_PHASE, 4.0), EPS);
        assertEquals(1.0f, RestfulMath.healPerStep(RestfulMath.FULL_MOON_PHASE, 4.0), EPS);
        assertEquals(2.5f, RestfulMath.healPerStep(2, 4.0), EPS);
    }

    // --- Bedroll half-strength (§7) ---

    @Test
    void bedrollHalvesTheFullMoonHeal() {
        // half strength on the full-moon floor: 0.5 HP per step vs a full bed's 1.0
        assertEquals(0.5f, RestfulMath.healPerStep(RestfulMath.FULL_MOON_PHASE, 2.0, 0.5), EPS);
        assertEquals(1.0f, RestfulMath.healPerStep(RestfulMath.FULL_MOON_PHASE, 2.0, 1.0), EPS);
    }

    @Test
    void bedrollHalfStrengthStacksWithTheGradient() {
        // a bedroll on a new moon heals ×2 × 0.5 = ×1.0 — exactly a full bed on
        // the full moon, and a real bed on a new moon still beats it (2.0).
        assertEquals(1.0f, RestfulMath.healPerStep(RestfulMath.NEW_MOON_PHASE, 2.0, 0.5), EPS);
        assertEquals(2.0f, RestfulMath.healPerStep(RestfulMath.NEW_MOON_PHASE, 2.0, 1.0), EPS);
        // and stacks mid-gradient: a bedroll on a quarter moon heals ×1.5 × 0.5 = ×0.75
        assertEquals(0.75f, RestfulMath.healPerStep(2, 2.0, 0.5), EPS);
    }

    @Test
    void bedrollStrengthExtremes() {
        assertEquals(0.0f, RestfulMath.healPerStep(0, 2.0, 0.0), EPS);
        assertEquals(0.0f, RestfulMath.healPerStep(RestfulMath.NEW_MOON_PHASE, 4.0, 0.0), EPS);
    }

    @Test
    void defaultStrengthEqualsAFullBed() {
        // the 2-arg overload is a full bed (strength 1.0) at every phase
        for (int phase = 0; phase < 8; phase++) {
            assertEquals(RestfulMath.healPerStep(phase, 2.0),
                    RestfulMath.healPerStep(phase, 2.0, 1.0), EPS,
                    "phase " + phase + " default must equal a full-strength bed");
        }
    }

    // --- Wake feedback (§2.7) ---

    @Test
    void wakeLineNeedsThreeHearts() {
        assertNull(RestfulMath.wakeLineKey(5.9f, false));
        assertNull(RestfulMath.wakeLineKey(5.9f, true));
        assertEquals("notification.respite.rested", RestfulMath.wakeLineKey(6.0f, false));
        assertEquals("notification.respite.rested", RestfulMath.wakeLineKey(20.0f, false));
    }

    @Test
    void anyDeepConversionUpgradesTheLine() {
        assertEquals("notification.respite.deep_rested", RestfulMath.wakeLineKey(6.0f, true));
    }

    // --- Full-night accounting (§2.2) ---

    /** A whole night simulated through the same pieces the handler runs. */
    private record NightResult(float health, float saturation, float restored, boolean deep) {
    }

    private static NightResult simulateNight(long nightTicks, int intervalTicks, float saturation,
            float health, float maxHealth, int moonPhase, double multiplier) {
        RestState state = new RestState();
        for (long t = 0; t < nightTicks; t++) {
            if (!state.tickAndCheckDue(intervalTicks)) {
                continue;
            }
            if (!RestfulMath.stepAllowed(saturation, health, maxHealth)) {
                continue;
            }
            saturation -= RestfulMath.SATURATION_COST_PER_STEP;
            float healed = Math.min(RestfulMath.healPerStep(moonPhase, multiplier), maxHealth - health);
            health += healed;
            state.recordConversion(healed, moonPhase == RestfulMath.NEW_MOON_PHASE);
        }
        return new NightResult(health, saturation, state.healthRestored(), state.deepConversionRan());
    }

    @Test
    void aFullNightIsTwentyStepsForTwentySaturation() {
        // 12,000 ticks at the 600 default = 20 conversions: 20 saturation → 20 health
        NightResult night = simulateNight(12_000, 600, 20.0f, 0.0f, 20.0f, 0, 2.0);
        assertEquals(20.0f, night.restored(), EPS);
        assertEquals(0.0f, night.saturation(), EPS);
        assertEquals(20.0f, night.health(), EPS);
        assertFalse(night.deep());
    }

    @Test
    void deepSleepFullHealsFromTenSaturation() {
        // the headline case: 10 saturation, half health, new moon → full by dawn
        NightResult night = simulateNight(12_000, 600, 10.0f, 0.0f, 20.0f,
                RestfulMath.NEW_MOON_PHASE, 2.0);
        assertEquals(20.0f, night.restored(), EPS);
        assertEquals(0.0f, night.saturation(), EPS);
        assertEquals(20.0f, night.health(), EPS);
        assertTrue(night.deep());
    }

    @Test
    void conversionHaltsAtTheSaturationFloor() {
        // 2.5 saturation funds exactly 2 steps; the 0.5 remainder is kept
        NightResult night = simulateNight(12_000, 600, 2.5f, 0.5f, 20.0f, 0, 2.0);
        assertEquals(2.0f, night.restored(), EPS);
        assertEquals(0.5f, night.saturation(), EPS);
    }

    @Test
    void conversionHaltsAtFullHealthAndKeepsTheRest() {
        // one heart missing: one step heals it, the other 19 steps spend nothing
        NightResult night = simulateNight(12_000, 600, 20.0f, 19.0f, 20.0f, 0, 2.0);
        assertEquals(1.0f, night.restored(), EPS);
        assertEquals(19.0f, night.saturation(), EPS);
        assertEquals(20.0f, night.health(), EPS);
    }

    @Test
    void aPartialFinalIntervalConvertsNothing() {
        // 599 ticks short of the 20th boundary → 19 steps, not 20
        NightResult night = simulateNight(11_999, 600, 20.0f, 0.0f, 20.0f, 0, 2.0);
        assertEquals(19.0f, night.restored(), EPS);
    }
}
