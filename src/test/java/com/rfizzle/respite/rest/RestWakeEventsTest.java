// Tier: 1 (pure JUnit)
package com.rfizzle.respite.rest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure gates behind the dawn-wake events: what counts as a dawn wake, and
 * when a sleep was touched by the time-lapse. The firing itself is covered by
 * the advancement gametests.
 */
class RestWakeEventsTest {

    @Test
    void dawnWakeNeedsDayAndSleep() {
        assertTrue(RestWakeEvents.isDawnWake(true, 100), "day + slept = dawn wake");
        assertFalse(RestWakeEvents.isDawnWake(false, 100), "still night = interrupted wake");
        assertFalse(RestWakeEvents.isDawnWake(true, 0), "no time slept is not a wake");
    }

    @Test
    void lapseTouchesSleepFromItsStartOnward() {
        assertFalse(RestWakeEvents.lapseTouchedSleep(-1, 0), "never active = untouched");
        assertFalse(RestWakeEvents.lapseTouchedSleep(40, 50), "active before the sleep began = untouched");
        assertTrue(RestWakeEvents.lapseTouchedSleep(50, 50), "active on the tick sleep began = touched");
        assertTrue(RestWakeEvents.lapseTouchedSleep(120, 50), "active during the sleep = touched");
    }
}
