// Tier: 1 (pure JUnit)
package com.rfizzle.respite.chronometer;

import com.rfizzle.respite.chronometer.ChronometerTime.LineVariant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the Chronometer's pure math to {@code design/SPEC.md} §5: the signal
 * formula at every boundary tick, the 12-hour clock derivation, the night
 * window, and the {@code (4 − phase) mod 8} new-moon countdown.
 */
class ChronometerTimeTest {

    @ParameterizedTest
    @CsvSource({
            // spec anchors: level 1 begins at dawn, level 8 holds sunset (12,000),
            // level 12 holds midnight (18,000), level 15 is the last stretch
            "0, 1",
            "1599, 1",
            "1600, 2",
            "11200, 8",
            "12000, 8",
            "12799, 8",
            "17999, 12",
            "18000, 12",
            "22400, 15",
            "23999, 15",
    })
    void signalBoundaries(long dayTime, int expected) {
        assertEquals(expected, ChronometerTime.signalFor(dayTime, false));
    }

    @ParameterizedTest
    @CsvSource({
            "24000, 1",    // next day wraps to level 1
            "35200, 8",    // day 2 sunset band
            "-1, 15",      // negative day time lands in the last band, not level 0
    })
    void signalWrapsAcrossDays(long dayTime, int expected) {
        assertEquals(expected, ChronometerTime.signalFor(dayTime, false));
    }

    @ParameterizedTest
    @CsvSource({"0", "6000", "12000", "18000", "23999"})
    void fixedTimeAlwaysReadsZero(long dayTime) {
        assertEquals(0, ChronometerTime.signalFor(dayTime, true));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 6:00 am",
            "999, 6:59 am",       // 999 × 60 / 1000 truncates to 59
            "1000, 7:00 am",
            "6000, 12:00 pm",     // noon
            "12500, 6:30 pm",
            "13200, 7:12 pm",     // the spec's worked example
            "18000, 12:00 am",    // midnight
            "23999, 5:59 am",
            "37200, 7:12 pm",     // day 2 reads the same clock
    })
    void clockTime(long dayTime, String expected) {
        assertEquals(expected, ChronometerTime.clockTime(dayTime));
    }

    @ParameterizedTest
    @CsvSource({"12000", "18000", "23999", "36000"})
    void nightWindowContains(long dayTime) {
        assertTrue(ChronometerTime.isNight(dayTime));
    }

    @ParameterizedTest
    @CsvSource({"0", "6000", "11999", "24000"})
    void nightWindowExcludes(long dayTime) {
        assertFalse(ChronometerTime.isNight(dayTime));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 4",   // full moon → four nights out
            "1, 3",
            "2, 2",
            "3, 1",
            "4, 0",   // new moon tonight
            "5, 7",
            "6, 6",
            "7, 5",
    })
    void newMoonCountdownForEveryPhase(int moonPhase, int expected) {
        assertEquals(expected, ChronometerTime.nightsUntilNewMoon(moonPhase));
    }

    @ParameterizedTest
    @CsvSource({
            "0, full",
            "1, waning_gibbous",
            "2, third_quarter",
            "3, waning_crescent",
            "4, new",
            "5, waxing_crescent",
            "6, first_quarter",
            "7, waxing_gibbous",
    })
    void moonPhaseKeys(int moonPhase, String suffix) {
        assertEquals("moon.respite." + suffix, ChronometerTime.moonPhaseKey(moonPhase));
    }

    @ParameterizedTest
    @CsvSource({
            "6000, false, 3, DAY",        // daytime: never a moon line
            "6000, false, 4, DAY",        // even on the new moon's day
            "18000, false, 3, NIGHT",
            "18000, false, 4, NEW_MOON",
            "12000, false, 0, NIGHT",     // the window opens at 12,000 exactly
            "11999, false, 4, DAY",
            "18000, true, 4, DAY",        // a fixed-time "night" carries no moon
    })
    void lineVariantGatesTheMoonOnTheNightWindow(long dayTime, boolean fixedTime, int moonPhase,
            LineVariant expected) {
        assertEquals(expected, ChronometerTime.lineVariant(dayTime, fixedTime, moonPhase));
    }
}
