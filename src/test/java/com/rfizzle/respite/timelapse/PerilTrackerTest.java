// Tier: 1 (pure JUnit)
package com.rfizzle.respite.timelapse;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the peril brake's state machine to {@code design/SPEC.md} §1: the
 * 100-real-tick window decay, targeting refresh (release lands exactly the
 * window after the last signal), target transfers, mob removal, and the
 * disconnect/stop cleanup paths.
 */
class PerilTrackerTest {

    private static final int WINDOW = 100;

    private final UUID player = UUID.randomUUID();
    private final UUID otherPlayer = UUID.randomUUID();
    private final UUID mob = UUID.randomUUID();
    private final UUID otherMob = UUID.randomUUID();

    private PerilTracker tracker;

    @BeforeEach
    void freshTracker() {
        tracker = new PerilTracker();
    }

    @Test
    void damageWindowDecaysAtExactlyOneHundredRealTicks() {
        tracker.recordCombat(player, 1000);
        assertTrue(tracker.isInPeril(player, 1000, WINDOW), "in peril on the damage tick");
        assertTrue(tracker.isInPeril(player, 1100, WINDOW), "still in peril at the window edge");
        assertFalse(tracker.isInPeril(player, 1101, WINDOW), "released one tick past the window");
    }

    @Test
    void freshDamageRefreshesTheWindow() {
        tracker.recordCombat(player, 1000);
        tracker.recordCombat(player, 1080);
        assertTrue(tracker.isInPeril(player, 1180, WINDOW));
        assertFalse(tracker.isInPeril(player, 1181, WINDOW));
    }

    @Test
    void targetingHoldsPerilWhileItLasts() {
        tracker.onMobTarget(mob, player);
        assertTrue(tracker.isInPeril(player, 1000, WINDOW));
        // far later, with no damage ever recorded — targeting alone holds
        assertTrue(tracker.isInPeril(player, 5000, WINDOW));
    }

    @Test
    void targetingReleaseLandsExactlyOneWindowAfterTheLastQuery() {
        tracker.onMobTarget(mob, player);
        assertTrue(tracker.isInPeril(player, 1000, WINDOW), "queried while targeted — refreshes");
        tracker.onMobTarget(mob, null);
        assertTrue(tracker.isInPeril(player, 1100, WINDOW), "tail window still holds");
        assertFalse(tracker.isInPeril(player, 1101, WINDOW), "released one tick past the tail");
    }

    @Test
    void mobRemovalReleasesItsClaim() {
        tracker.onMobTarget(mob, player);
        tracker.isInPeril(player, 1000, WINDOW);
        tracker.onMobRemoved(mob);
        assertFalse(tracker.isInPeril(player, 1101, WINDOW), "removal starts the tail, not a hold");
    }

    @Test
    void targetTransferMovesTheClaimBetweenPlayers() {
        tracker.onMobTarget(mob, player);
        tracker.onMobTarget(mob, otherPlayer);
        assertTrue(tracker.isInPeril(otherPlayer, 2000, WINDOW), "new target is in peril");
        assertFalse(tracker.isInPeril(player, 2000, WINDOW),
                "old target released (no prior query, so no tail window)");
    }

    @Test
    void twoMobsBothMustReleaseBeforeTheTailStarts() {
        tracker.onMobTarget(mob, player);
        tracker.onMobTarget(otherMob, player);
        tracker.onMobTarget(mob, null);
        assertTrue(tracker.isInPeril(player, 3000, WINDOW), "one hostile still hunting");
        tracker.onMobTarget(otherMob, null);
        assertTrue(tracker.isInPeril(player, 3100, WINDOW), "tail from the last refresh at 3000");
        assertFalse(tracker.isInPeril(player, 3101, WINDOW));
    }

    @Test
    void repeatedSetTargetOnTheSamePlayerCountsOnce() {
        tracker.onMobTarget(mob, player);
        tracker.onMobTarget(mob, player);
        tracker.onMobTarget(mob, null);
        // a double-count would survive the single release and hold forever
        assertFalse(tracker.isInPeril(player, 5000, WINDOW));
    }

    @Test
    void forgetPlayerDropsTheDamageWindowButNotTheMobsClaim() {
        tracker.recordCombat(player, 1000);
        tracker.onMobTarget(mob, player);
        tracker.forgetPlayer(player);
        // a rejoining player still hunted by the same mob is still in peril
        assertTrue(tracker.isInPeril(player, 1001, WINDOW));
        tracker.onMobRemoved(mob);
        tracker.forgetPlayer(player);
        assertFalse(tracker.isInPeril(player, 1002, WINDOW));
    }

    @Test
    void clearEmptiesEverything() {
        tracker.recordCombat(player, 1000);
        tracker.onMobTarget(mob, otherPlayer);
        assertFalse(tracker.isEmpty());
        tracker.clear();
        assertTrue(tracker.isEmpty());
        assertFalse(tracker.isInPeril(player, 1001, WINDOW));
        assertFalse(tracker.isInPeril(otherPlayer, 1001, WINDOW));
    }

    @Test
    void expiredWindowsSelfPrune() {
        tracker.recordCombat(player, 1000);
        assertFalse(tracker.isInPeril(player, 2000, WINDOW));
        assertTrue(tracker.isEmpty(), "an expired entry prunes on the read that rejects it");
    }
}
