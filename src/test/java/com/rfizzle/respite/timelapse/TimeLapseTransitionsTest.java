// Tier: 1 (pure JUnit)
package com.rfizzle.respite.timelapse;

import com.rfizzle.respite.timelapse.TimeLapseTransitions.Announcement;
import com.rfizzle.respite.timelapse.TimeLapseTransitions.Cue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the announcement state machine to {@code design/SPEC.md} §1 and
 * §Sound Design: the start cue fires once per lapse episode, mid-lapse rate
 * changes update the line without re-firing it, the peril brake swaps lines
 * silently in both directions, and the settle cue closes the episode.
 */
class TimeLapseTransitionsTest {

    private TimeLapseTransitions transitions;

    @BeforeEach
    void freshMachine() {
        transitions = new TimeLapseTransitions();
    }

    @Test
    void quietWhileNothingHappens() {
        assertNull(transitions.evaluate(LapseState.SETTLED, 1, 0, 4));
        assertNull(transitions.evaluate(LapseState.SETTLED, 1, 0, 4));
    }

    @Test
    void lapseStartFiresTheStartCueOnce() {
        Announcement start = transitions.evaluate(LapseState.ACTIVE, 30, 2, 4);
        assertNotNull(start);
        assertEquals(Cue.START, start.cue());
        assertEquals(30, start.rate());
        assertNull(transitions.evaluate(LapseState.ACTIVE, 30, 2, 4), "steady state is silent");
    }

    @Test
    void midLapseRateChangeUpdatesTheLineWithoutACue() {
        transitions.evaluate(LapseState.ACTIVE, 30, 2, 4);
        Announcement change = transitions.evaluate(LapseState.ACTIVE, 45, 3, 4);
        assertNotNull(change);
        assertEquals(Cue.NONE, change.cue());
        assertEquals(45, change.rate());
    }

    @Test
    void sleeperCountChangeAtTheSameRateStillUpdatesTheLine() {
        transitions.evaluate(LapseState.ACTIVE, 30, 2, 4);
        Announcement change = transitions.evaluate(LapseState.ACTIVE, 30, 1, 2);
        assertNotNull(change, "the k-of-n text changed even though the rate held");
        assertEquals(Cue.NONE, change.cue());
    }

    @Test
    void settleFiresTheEndCue() {
        transitions.evaluate(LapseState.ACTIVE, 30, 2, 4);
        Announcement settle = transitions.evaluate(LapseState.SETTLED, 1, 0, 4);
        assertNotNull(settle);
        assertEquals(LapseState.SETTLED, settle.state());
        assertEquals(Cue.END, settle.cue());
    }

    @Test
    void perilBrakeSwapsLinesSilentlyBothWays() {
        transitions.evaluate(LapseState.ACTIVE, 30, 2, 4);
        Announcement hold = transitions.evaluate(LapseState.HELD, 1, 2, 4);
        assertNotNull(hold);
        assertEquals(LapseState.HELD, hold.state());
        assertEquals(Cue.NONE, hold.cue(), "the hold is silent — the line carries it");
        assertNull(transitions.evaluate(LapseState.HELD, 1, 2, 4), "holding steady is silent");
        Announcement resume = transitions.evaluate(LapseState.ACTIVE, 30, 2, 4);
        assertNotNull(resume);
        assertEquals(Cue.NONE, resume.cue(), "resuming an open episode must not re-fire the start cue");
    }

    @Test
    void settlingOutOfAHoldClosesTheEpisode() {
        transitions.evaluate(LapseState.ACTIVE, 30, 2, 4);
        transitions.evaluate(LapseState.HELD, 1, 2, 4);
        Announcement settle = transitions.evaluate(LapseState.SETTLED, 1, 0, 4);
        assertNotNull(settle);
        assertEquals(Cue.END, settle.cue(), "the episode opened, so leaving it must close audibly");
    }

    @Test
    void holdWithoutAnOpenEpisodeNeverCues() {
        // sleepers get in bed mid-fight: the lapse never audibly started
        Announcement hold = transitions.evaluate(LapseState.HELD, 1, 2, 4);
        assertNotNull(hold);
        assertEquals(Cue.NONE, hold.cue());
        Announcement settle = transitions.evaluate(LapseState.SETTLED, 1, 0, 4);
        assertNotNull(settle, "the hold line was shown, so the settle line closes the narrative");
        assertEquals(Cue.NONE, settle.cue(), "no start means no end cue");
    }

    @Test
    void holdThenFightEndsOpensTheEpisodeAudibly() {
        transitions.evaluate(LapseState.HELD, 1, 2, 4);
        Announcement start = transitions.evaluate(LapseState.ACTIVE, 30, 2, 4);
        assertNotNull(start);
        assertEquals(Cue.START, start.cue(), "first audible start of this episode");
    }

    @Test
    void nextNightStartsAFreshEpisode() {
        transitions.evaluate(LapseState.ACTIVE, 30, 2, 4);
        transitions.evaluate(LapseState.SETTLED, 1, 0, 4);
        Announcement start = transitions.evaluate(LapseState.ACTIVE, 60, 4, 4);
        assertNotNull(start);
        assertEquals(Cue.START, start.cue());
    }

    @Test
    void resetForgetsTheOpenEpisode() {
        transitions.evaluate(LapseState.ACTIVE, 30, 2, 4);
        transitions.reset();
        assertNull(transitions.evaluate(LapseState.SETTLED, 1, 0, 4),
                "a fresh machine has nothing to settle");
        Announcement start = transitions.evaluate(LapseState.ACTIVE, 30, 2, 4);
        assertNotNull(start);
        assertEquals(Cue.START, start.cue());
    }
}
