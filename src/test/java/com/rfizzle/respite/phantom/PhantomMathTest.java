// Tier: 1 (pure JUnit)
package com.rfizzle.respite.phantom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The phantom anchor-eligibility rule ({@code design/SPEC.md} §3): night and
 * mode and sky are all hard requirements, and the altitude-or-new-moon disjunction
 * with the {@code phantomNewMoon} toggle, plus the strict altitude boundary.
 */
class PhantomMathTest {

    private static final int ALTITUDE_MIN = 100;

    /** A high, sky-lit, awake survival anchor at night — the mountain-watch case. */
    private static boolean highAnchor() {
        return PhantomMath.isAnchorEligible(true, true, false, true, 150, ALTITUDE_MIN, false, true);
    }

    // --- Hard requirements: any one false disqualifies ---

    @Test
    void aQualifyingHighAnchorIsEligible() {
        assertTrue(highAnchor());
    }

    @Test
    void daytimeIsNeverAnchor() {
        assertFalse(PhantomMath.isAnchorEligible(false, true, false, true, 150, ALTITUDE_MIN, false, true));
    }

    @Test
    void nonSurvivalAdventureIsNeverAnchor() {
        // Creative and spectator collapse to survivalOrAdventure=false at the call site.
        assertFalse(PhantomMath.isAnchorEligible(true, false, false, true, 150, ALTITUDE_MIN, false, true));
    }

    @Test
    void sleepingIsNeverAnchor() {
        assertFalse(PhantomMath.isAnchorEligible(true, true, true, true, 150, ALTITUDE_MIN, false, true));
    }

    @Test
    void noSkyAccessIsNeverAnchor() {
        assertFalse(PhantomMath.isAnchorEligible(true, true, false, false, 150, ALTITUDE_MIN, false, true));
    }

    @Test
    void newMoonUnderCoverIsStillNoAnchor() {
        // Even a new moon can't overcome a missing sky requirement.
        assertFalse(PhantomMath.isAnchorEligible(true, true, false, false, 40, ALTITUDE_MIN, true, true));
    }

    // --- Altitude branch ---

    @Test
    void feetStrictlyAboveAltitudeQualify() {
        assertTrue(PhantomMath.isAnchorEligible(true, true, false, true, ALTITUDE_MIN + 1, ALTITUDE_MIN, false, true));
    }

    @Test
    void feetExactlyAtAltitudeAreNotAbove() {
        assertFalse(PhantomMath.isAnchorEligible(true, true, false, true, ALTITUDE_MIN, ALTITUDE_MIN, false, true));
    }

    @Test
    void belowAltitudeOffNewMoonIsNoAnchor() {
        assertFalse(PhantomMath.isAnchorEligible(true, true, false, true, 63, ALTITUDE_MIN, false, true));
    }

    // --- New-moon branch ---

    @Test
    void belowAltitudeOnNewMoonQualifiesWhenEnabled() {
        assertTrue(PhantomMath.isAnchorEligible(true, true, false, true, 63, ALTITUDE_MIN, true, true));
    }

    @Test
    void newMoonToggleOffIgnoresTheNewMoon() {
        assertFalse(PhantomMath.isAnchorEligible(true, true, false, true, 63, ALTITUDE_MIN, true, false));
    }

    @Test
    void aboveAltitudeQualifiesRegardlessOfTheNewMoonToggle() {
        // The altitude branch alone carries eligibility even with new-moon spawning disabled.
        assertTrue(PhantomMath.isAnchorEligible(true, true, false, true, 150, ALTITUDE_MIN, true, false));
    }
}
