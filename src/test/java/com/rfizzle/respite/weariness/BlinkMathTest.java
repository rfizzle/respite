// Tier: 1 (pure JUnit)
package com.rfizzle.respite.weariness;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Exhausted blink timing ({@code design/SPEC.md} §4.4): the 60–120s jitter
 * bounds, the ease curve peaking at 55% and never beyond, and the combat
 * suppression that defers — never skips — a due blink. Pure — no client bootstrap.
 */
class BlinkMathTest {

    @Test
    void jitterStaysWithinTheSixtyToOneTwentySecondBand() {
        assertEquals(1200, BlinkMath.nextInterval(0.0), "floor is 60s");
        assertEquals(2400, BlinkMath.nextInterval(1.0), "ceiling is 120s");
        assertEquals(1800, BlinkMath.nextInterval(0.5), "centre is the nominal 90s");
        for (int i = 0; i <= 100; i++) {
            int interval = BlinkMath.nextInterval(i / 100.0);
            assertTrue(interval >= BlinkMath.MIN_INTERVAL_TICKS && interval <= BlinkMath.MAX_INTERVAL_TICKS,
                    "interval " + interval + " out of the jitter band");
        }
    }

    @Test
    void occlusionRisesToThePeakAndBackWithoutExceedingIt() {
        float max = 0.0f;
        for (long t = -2; t <= BlinkMath.BLINK_DURATION_TICKS + 2; t++) {
            float occ = BlinkMath.occlusionAt(t);
            assertTrue(occ >= 0.0f, "occlusion never negative");
            assertTrue(occ <= BlinkMath.PEAK_OCCLUSION + 1.0e-6f,
                    "occlusion " + occ + " exceeds the 55% peak at t=" + t);
            max = Math.max(max, occ);
        }
        assertEquals(BlinkMath.PEAK_OCCLUSION, max, 1.0e-6f, "the blink must reach its 55% peak");
    }

    @Test
    void occlusionIsZeroOutsideTheBlink() {
        assertEquals(0.0f, BlinkMath.occlusionAt(-1));
        assertEquals(0.0f, BlinkMath.occlusionAt(BlinkMath.BLINK_DURATION_TICKS));
        assertTrue(BlinkMath.isBlinkDone(BlinkMath.BLINK_DURATION_TICKS));
        assertFalse(BlinkMath.isBlinkDone(BlinkMath.BLINK_DURATION_TICKS - 1));
    }

    @Test
    void noCombatObservedNeverSuppresses() {
        assertTrue(BlinkMath.canBlink(0, -1));
        assertTrue(BlinkMath.canBlink(10_000, -1));
    }

    @Test
    void combatSuppressesForExactlyTenSeconds() {
        long combatAt = 500;
        assertFalse(BlinkMath.canBlink(combatAt, combatAt), "suppressed the instant combat lands");
        assertFalse(BlinkMath.canBlink(combatAt + BlinkMath.COMBAT_SUPPRESS_TICKS - 1, combatAt),
                "still suppressed one tick before the window clears");
        assertTrue(BlinkMath.canBlink(combatAt + BlinkMath.COMBAT_SUPPRESS_TICKS, combatAt),
                "clear exactly at the 200-tick boundary");
    }

    @Test
    void aDueBlinkDefersThroughCombatThenFiresOnceTheWindowClears() {
        long nextBlinkTick = 1000;
        long combatAt = 950; // combat 50 ticks before the blink is due
        // Due (now >= nextBlinkTick) but inside the 200-tick window: it must not start.
        assertFalse(BlinkMath.shouldStartBlink(1000, nextBlinkTick, combatAt, false),
                "a due blink inside the combat window is held, not fired");
        assertFalse(BlinkMath.shouldStartBlink(combatAt + BlinkMath.COMBAT_SUPPRESS_TICKS - 1,
                nextBlinkTick, combatAt, false), "still held one tick early");
        // The tick the window clears, the still-due blink fires — deferred, not skipped.
        long cleared = combatAt + BlinkMath.COMBAT_SUPPRESS_TICKS;
        assertTrue(BlinkMath.shouldStartBlink(cleared, nextBlinkTick, combatAt, false),
                "the deferred blink fires the moment the window clears");
    }

    @Test
    void anAlreadyRunningBlinkDoesNotRestart() {
        assertFalse(BlinkMath.shouldStartBlink(5000, 1000, -1, true),
                "no new blink starts while one is already running");
    }

    @Test
    void aBlinkNotYetDueDoesNotStart() {
        assertFalse(BlinkMath.shouldStartBlink(999, 1000, -1, false));
        assertTrue(BlinkMath.shouldStartBlink(1000, 1000, -1, false));
    }

    @Test
    void onlyADamagingHitCountsAsCombat() {
        // alive, attackable, not invulnerable: a real hit — arms the window.
        assertTrue(BlinkMath.attackDealsDamage(true, true, false),
                "a live, attackable, vulnerable target counts as dealing damage");
        // An invulnerable target lands no damage — the issue's repro case.
        assertFalse(BlinkMath.attackDealsDamage(true, true, true),
                "an invulnerable target never arms the window");
        // Unattackable (marker armor stand, spectator): no damage.
        assertFalse(BlinkMath.attackDealsDamage(true, false, false),
                "an unattackable target never arms the window");
        // A corpse in a post-kill client frame: no damage.
        assertFalse(BlinkMath.attackDealsDamage(false, true, false),
                "a dead target never arms the window");
    }
}
