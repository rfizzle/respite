package com.rfizzle.respite.chronometer;

/**
 * The Chronometer's pure time math ({@code design/SPEC.md} §5) — the signal
 * formula, the 12-hour clock, the night window, and the new-moon countdown.
 * One formula, one home: the block, the inspect line, the Jade/WTHIT line,
 * and (later) {@code /respite status} and the public API all read from here.
 *
 * <p>Deliberately free of {@code net.minecraft} types so the whole class unit
 * tests without a Fabric bootstrap: callers pass the level's day time and
 * dimension facts as primitives.
 */
public final class ChronometerTime {

    /** World ticks in a Minecraft day. */
    public static final long DAY_LENGTH = 24000L;

    /** World ticks spanned by each of the 15 signal levels. */
    public static final long TICKS_PER_LEVEL = 1600L;

    /** First tick of the night window (day-time position), inclusive. */
    public static final long NIGHT_START = 12000L;

    /** Moon-phase lang-key suffixes, indexed by the vanilla phase number (0 = full moon). */
    private static final String[] MOON_PHASE_KEYS = {
            "full", "waning_gibbous", "third_quarter", "waning_crescent",
            "new", "waxing_crescent", "first_quarter", "waxing_gibbous",
    };

    /** Which inspect/tooltip line a given moment gets. */
    public enum LineVariant {
        /** Clock and signal only — daytime, or any fixed-time dimension. */
        DAY,
        /** Clock, signal, moon phase, and the countdown. */
        NIGHT,
        /** Clock, signal, and "new moon tonight". */
        NEW_MOON,
    }

    private ChronometerTime() {
    }

    /**
     * The redstone power for a day time: {@code floor((dayTime mod 24000) / 1600) + 1},
     * 1–15 with each level spanning 1,600 ticks. Fixed-time dimensions read 0 —
     * the clock spins uselessly there, so the Chronometer honestly says nothing.
     */
    public static int signalFor(long dayTime, boolean fixedTime) {
        if (fixedTime) {
            return 0;
        }
        return (int) (dayPosition(dayTime) / TICKS_PER_LEVEL) + 1;
    }

    /**
     * The 12-hour clock reading, e.g. {@code "7:12 pm"}. Hours derive as
     * {@code ((dayTime / 1000) + 6) mod 24} (day time 0 is 6:00 am), minutes as
     * {@code (dayTime mod 1000) × 60 / 1000}.
     */
    public static String clockTime(long dayTime) {
        long position = dayPosition(dayTime);
        int hours = (int) ((position / 1000 + 6) % 24);
        int minutes = (int) (position % 1000 * 60 / 1000);
        int hour12 = hours % 12 == 0 ? 12 : hours % 12;
        return hour12 + ":" + (minutes < 10 ? "0" : "") + minutes + (hours < 12 ? " am" : " pm");
    }

    /** True in the night window — day-time position 12,000–23,999. */
    public static boolean isNight(long dayTime) {
        return dayPosition(dayTime) >= NIGHT_START;
    }

    /**
     * Nights until the next new moon: {@code (4 − moonPhase) mod 8}, 0 when
     * tonight's moon (vanilla phase 4) is the new moon.
     */
    public static int nightsUntilNewMoon(int moonPhase) {
        return Math.floorMod(4 - moonPhase, 8);
    }

    /** The lang key for a vanilla moon phase, {@code moon.respite.<phase>}. */
    public static String moonPhaseKey(int moonPhase) {
        return "moon.respite." + MOON_PHASE_KEYS[Math.floorMod(moonPhase, 8)];
    }

    /**
     * Which line variant a moment gets: the moon addition only appears in the
     * night window of a cycling dimension — a fixed-time dimension's frozen
     * "night" carries no meaningful moon.
     */
    public static LineVariant lineVariant(long dayTime, boolean fixedTime, int moonPhase) {
        if (fixedTime || !isNight(dayTime)) {
            return LineVariant.DAY;
        }
        return nightsUntilNewMoon(moonPhase) == 0 ? LineVariant.NEW_MOON : LineVariant.NIGHT;
    }

    /** Position within the current day, 0–23,999, safe for negative day times. */
    private static long dayPosition(long dayTime) {
        return Math.floorMod(dayTime, DAY_LENGTH);
    }
}
