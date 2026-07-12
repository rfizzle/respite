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

    /** The {@code alarm_hour} blockstate value meaning "no alarm set". */
    public static final int ALARM_OFF = 24;

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

    /**
     * The comparator's moon reading, 0–15: a fullness ramp keyed off the moon's
     * distance from the new moon (vanilla phase 4). New moon reads 0 (dark),
     * full moon 15, the quarters ~8; the half-illuminated phases sit symmetric
     * either side. {@code round(|phase − 4| × 15 / 4)} → 15, 11, 8, 4, 0, 4, 8, 11
     * for phases 0–7.
     */
    public static int moonFullness(int moonPhase) {
        int distance = Math.abs(Math.floorMod(moonPhase, 8) - 4);
        return (distance * 15 + 2) / 4;
    }

    /**
     * The day-time position at which a wall-clock alarm hour (0–23) arrives:
     * {@code ((hour − 6) mod 24) × 1000}, the inverse of {@link #clockTime}'s
     * {@code hours = ((dayTime / 1000) + 6) mod 24}. Hour 6 (6 am) maps to 0,
     * hour 0 (midnight) to 18,000.
     */
    public static long alarmBoundary(int hour) {
        return Math.floorMod(hour - 6, 24) * 1000L;
    }

    /**
     * Whether an alarm set to {@code alarmHour} arrives within the current
     * {@code interval}-tick window — true exactly once per day for a tick grid
     * of period {@code interval}, because such a grid lands in each half-open
     * window {@code [boundary, boundary + interval)} exactly once. {@link #ALARM_OFF}
     * never fires. Callers still gate on {@code doDaylightCycle}: with time
     * frozen no window is ever crossed, so a stopped clock never chimes.
     */
    public static boolean alarmFires(long dayTime, int alarmHour, long interval) {
        if (alarmHour == ALARM_OFF) {
            return false;
        }
        return Math.floorMod(dayPosition(dayTime) - alarmBoundary(alarmHour), DAY_LENGTH) < interval;
    }

    /**
     * The next {@code alarm_hour} value when the block is sneak-cycled:
     * {@code off → 0 → 1 → … → 23 → off}. A single {@code (value + 1) mod 25}
     * over the 0–24 range, with {@link #ALARM_OFF} (24) the wrap point.
     */
    public static int cycleAlarm(int alarmHour) {
        return Math.floorMod(alarmHour + 1, ALARM_OFF + 1);
    }

    /**
     * A whole wall-clock hour (0–23) as a 12-hour label, e.g. {@code "6:00 am"},
     * {@code "12:00 am"} for midnight, {@code "12:00 pm"} for noon.
     */
    public static String hourLabel(int hour) {
        int h = Math.floorMod(hour, 24);
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + ":00" + (h < 12 ? " am" : " pm");
    }

    /** Position within the current day, 0–23,999, safe for negative day times. */
    private static long dayPosition(long dayTime) {
        return Math.floorMod(dayTime, DAY_LENGTH);
    }
}
