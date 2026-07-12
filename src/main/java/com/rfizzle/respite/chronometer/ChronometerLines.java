package com.rfizzle.respite.chronometer;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;

/**
 * Assembles the Chronometer's one-line reading from a key prefix and the
 * level's time facts. The inspect action bar passes
 * {@code notification.respite.chronometer}, the Jade/WTHIT line passes
 * {@code tooltip.respite.chronometer}; the night window appends {@code _night}
 * or {@code _new_moon} to the prefix, so both surfaces stay identical by
 * construction. A set alarm hour appends a {@code _alarm} segment on top of any
 * variant. All decisions live in {@link ChronometerTime} — this class is only
 * the {@link Component} shell.
 */
public final class ChronometerLines {

    private ChronometerLines() {
    }

    /** The line for a placed block right now, reading its alarm hour off the state. */
    public static Component build(String keyPrefix, Level level, int alarmHour) {
        return build(keyPrefix, level.getDayTime(), level.dimensionType().hasFixedTime(),
                level.getMoonPhase(), alarmHour);
    }

    /** The line for explicit time facts (the seam the gametests drive). */
    public static Component build(String keyPrefix, long dayTime, boolean fixedTime, int moonPhase, int alarmHour) {
        String clock = ChronometerTime.clockTime(dayTime);
        int signal = ChronometerTime.signalFor(dayTime, fixedTime);
        MutableComponent line = switch (ChronometerTime.lineVariant(dayTime, fixedTime, moonPhase)) {
            case DAY -> Component.translatable(keyPrefix, clock, signal);
            case NEW_MOON -> Component.translatable(keyPrefix + "_new_moon", clock, signal);
            case NIGHT -> Component.translatable(keyPrefix + "_night", clock, signal,
                    Component.translatable(ChronometerTime.moonPhaseKey(moonPhase)),
                    ChronometerTime.nightsUntilNewMoon(moonPhase));
        };
        if (alarmHour != ChronometerTime.ALARM_OFF) {
            line.append(Component.translatable(keyPrefix + "_alarm", ChronometerTime.hourLabel(alarmHour)));
        }
        return line;
    }
}
