// Tier: 1 (pure JUnit)
package com.rfizzle.respite.restful;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The armed-sleeper lifecycle and per-night accounting ({@code design/SPEC.md}
 * §2): arming, the interval counter's due/reset cadence, the health tally and
 * sticky Deep Sleep flag, and the wake/disconnect/server-stop clears.
 */
class RestfulTrackerTest {

    private static final float EPS = 1e-6f;

    @Test
    void onlyArmedPlayersAreTracked() {
        RestfulTracker tracker = new RestfulTracker();
        UUID armed = UUID.randomUUID();
        assertTrue(tracker.isEmpty());
        assertNull(tracker.get(armed));
        tracker.arm(armed);
        assertNotNull(tracker.get(armed));
        assertNull(tracker.get(UUID.randomUUID()));
        assertFalse(tracker.isEmpty());
    }

    @Test
    void forgetHandsBackTheNightAndDropsIt() {
        RestfulTracker tracker = new RestfulTracker();
        UUID player = UUID.randomUUID();
        tracker.arm(player);
        RestState state = tracker.get(player);
        assertSame(state, tracker.forget(player));
        assertNull(tracker.get(player));
        assertNull(tracker.forget(player), "a second forget (disconnect after wake) is a safe no-op");
    }

    @Test
    void reArmingStartsAFreshNight() {
        RestfulTracker tracker = new RestfulTracker();
        UUID player = UUID.randomUUID();
        tracker.arm(player);
        tracker.get(player).recordConversion(4.0f, true);
        tracker.arm(player);
        assertEquals(0.0f, tracker.get(player).healthRestored(), EPS);
        assertFalse(tracker.get(player).deepConversionRan());
    }

    @Test
    void clearDropsEveryone() {
        RestfulTracker tracker = new RestfulTracker();
        tracker.arm(UUID.randomUUID());
        tracker.arm(UUID.randomUUID());
        tracker.clear();
        assertTrue(tracker.isEmpty());
    }

    @Test
    void intervalCounterFallsDueExactlyOnTheBoundaryAndResets() {
        RestState state = new RestState();
        for (int t = 1; t <= 599; t++) {
            assertFalse(state.tickAndCheckDue(600), "tick " + t + " must not be due");
        }
        assertTrue(state.tickAndCheckDue(600), "tick 600 must fall due");
        for (int t = 601; t <= 1199; t++) {
            assertFalse(state.tickAndCheckDue(600), "tick " + t + " must not be due");
        }
        assertTrue(state.tickAndCheckDue(600), "tick 1200 must fall due");
        assertEquals(1200, state.ticksSlept());
    }

    @Test
    void aShorterReloadedIntervalTakesEffectAtTheNextBoundary() {
        RestState state = new RestState();
        for (int t = 0; t < 150; t++) {
            state.tickAndCheckDue(600);
        }
        // reload dropped the interval to 100; the 150 already banked qualify at once
        assertTrue(state.tickAndCheckDue(100));
    }

    @Test
    void theTallyAccumulatesAndTheDeepFlagSticks() {
        RestState state = new RestState();
        state.recordConversion(1.0f, false);
        state.recordConversion(2.0f, true);
        state.recordConversion(0.5f, false);
        assertEquals(3.5f, state.healthRestored(), EPS);
        assertTrue(state.deepConversionRan(), "one deep conversion must mark the whole night");
    }
}
