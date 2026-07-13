// Tier: 1 (pure JUnit)
package com.rfizzle.respite.chronometer;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pocket chronometer's pure logic ({@code design/SPEC.md} §5): the tooltip
 * variant selection and format-arg shape (day / night / new-moon / fixed-time
 * still, plus the always-present days-awake line), and the days-awake refresh
 * policy that keeps a held stack from resyncing every tick.
 */
class PocketChronometerTest {

    private static final String KEY = "tooltip.respite.pocket_chronometer";

    private static TranslatableContents contents(Component component) {
        assertInstanceOf(TranslatableContents.class, component.getContents(),
                "pocket chronometer lines are translatable");
        return (TranslatableContents) component.getContents();
    }

    // --- tooltip variants -------------------------------------------------

    @Test
    void daytimeShowsTheClockThenTheAwakeLine() {
        List<Component> lines = PocketChronometer.tooltip(1000L, false, 0, 0);
        assertEquals(2, lines.size(), "a day reading is the time line plus the awake line");
        assertEquals(KEY, contents(lines.get(0)).getKey());
        assertEquals(1, contents(lines.get(0)).getArgs().length, "day line carries only the clock");
        assertEquals(KEY + "_awake", contents(lines.get(1)).getKey());
        assertEquals(1, contents(lines.get(1)).getArgs().length, "awake line carries the days figure");
    }

    @Test
    void nightAddsTheMoonPhaseAndCountdown() {
        // Day time 13000 is in the night window; moon phase 0 (full) is 4 nights from new.
        List<Component> lines = PocketChronometer.tooltip(13000L, false, 0, 0);
        assertEquals(KEY + "_night", contents(lines.get(0)).getKey());
        assertEquals(3, contents(lines.get(0)).getArgs().length, "night line carries clock, phase, and count");
    }

    @Test
    void theNewMoonNightDropsTheCountdown() {
        // Moon phase 4 is the new moon: the countdown reads "tonight", not a number.
        List<Component> lines = PocketChronometer.tooltip(13000L, false, 4, 0);
        assertEquals(KEY + "_new_moon", contents(lines.get(0)).getKey());
        assertEquals(1, contents(lines.get(0)).getArgs().length, "new-moon line carries only the clock");
    }

    @Test
    void aFixedTimeDimensionShowsTheStillNoteNotABogusClock() {
        List<Component> lines = PocketChronometer.tooltip(13000L, true, 4, 0);
        assertEquals(KEY + "_still", contents(lines.get(0)).getKey());
        assertEquals(0, contents(lines.get(0)).getArgs().length, "the still note takes no clock");
        assertEquals(KEY + "_awake", contents(lines.get(1)).getKey(),
                "days awake still shows in a fixed-time dimension — it is the holder's, not the sky's");
    }

    // --- days-awake refresh policy ---------------------------------------

    @Test
    void awakeTenthsBucketsToTheDisplayedTenthOfADay() {
        assertEquals(0L, PocketChronometer.awakeTenths(0));
        assertEquals(1L, PocketChronometer.awakeTenths(2400));   // 0.1 day
        assertEquals(35L, PocketChronometer.awakeTenths(84_000)); // 3.5 days
    }

    @Test
    void refreshHoldsWithinATenthAndFiresOnCrossingIt() {
        assertFalse(PocketChronometer.refreshDue(0, 100), "a fresh sub-tenth read is not worth a resync");
        assertFalse(PocketChronometer.refreshDue(2400, 2500), "still 0.1 day — no rewrite");
        assertTrue(PocketChronometer.refreshDue(0, 2400), "crossing to 0.1 day rewrites");
        assertTrue(PocketChronometer.refreshDue(2400, 4800), "crossing to 0.2 day rewrites");
    }
}
