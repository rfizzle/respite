// Tier: 1 (pure JUnit)
package com.rfizzle.respite.command;

import com.rfizzle.respite.weariness.WearinessStage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The pure formatting behind {@code /respite status}: the time-awake figure and
 * the rest-stage translation key.
 */
class StatusFormatTest {

    @Test
    void awakeDaysIsOneDecimalDayCount() {
        assertEquals("0.0", StatusFormat.awakeDays(0));
        assertEquals("1.0", StatusFormat.awakeDays(24_000));
        assertEquals("1.5", StatusFormat.awakeDays(36_000));
        assertEquals("3.0", StatusFormat.awakeDays(72_000));
    }

    @Test
    void restStageRoutesThroughTranslationKeys() {
        assertEquals("command.respite.status.stage.rested", StatusFormat.restStageKey(WearinessStage.NONE));
        assertEquals("command.respite.status.stage.weary", StatusFormat.restStageKey(WearinessStage.WEARY));
        assertEquals("command.respite.status.stage.exhausted", StatusFormat.restStageKey(WearinessStage.EXHAUSTED));
    }
}
