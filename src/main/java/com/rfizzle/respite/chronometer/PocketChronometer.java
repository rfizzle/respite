package com.rfizzle.respite.chronometer;

import com.rfizzle.respite.command.StatusFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The pocket chronometer's tooltip assembly and its days-awake refresh policy
 * ({@code design/SPEC.md} §5, the portable half of the Chronometer). The carried
 * timepiece reads four facts — the hour, the moon phase, the nights until the new
 * moon, and the holder's days awake — with no redstone signal, so it cannot reuse
 * {@link ChronometerLines} (which always carries the block's signal). Every
 * decision still routes through {@link ChronometerTime} and {@link StatusFormat},
 * so the item reads the same clock as the block and the same days-awake figure as
 * {@code /respite status} by construction.
 *
 * <p>The tooltip half touches {@link Component} but reads only values the caller
 * supplies as primitives; the refresh half ({@link #refreshDue}, {@link #awakeTenths})
 * is free of {@code net.minecraft} types, so both unit-test without a bootstrap.
 */
public final class PocketChronometer {

    /** Tooltip lang-key prefix — the signal-free reading distinct from the block's {@code tooltip.respite.chronometer}. */
    static final String KEY = "tooltip.respite.pocket_chronometer";

    /** World ticks whose passage moves the days-awake reading by one displayed tenth of a day. */
    private static final long TICKS_PER_TENTH = ChronometerTime.DAY_LENGTH / 10;

    private PocketChronometer() {
    }

    /**
     * The pocket chronometer's tooltip lines for a moment and a holder: the time
     * line (or, in a fixed-time dimension, the honest "still" note that mirrors the
     * block's still dial), and the always-present days-awake line below it.
     */
    public static List<Component> tooltip(long dayTime, boolean fixedTime, int moonPhase, int awakeTicks) {
        List<Component> lines = new ArrayList<>(2);
        lines.add(timeLine(dayTime, fixedTime, moonPhase));
        lines.add(Component.translatable(KEY + "_awake", StatusFormat.awakeDays(awakeTicks))
                .withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private static Component timeLine(long dayTime, boolean fixedTime, int moonPhase) {
        if (fixedTime) {
            return Component.translatable(KEY + "_still").withStyle(ChatFormatting.GRAY);
        }
        String clock = ChronometerTime.clockTime(dayTime);
        MutableComponent line = switch (ChronometerTime.lineVariant(dayTime, false, moonPhase)) {
            case DAY -> Component.translatable(KEY, clock);
            case NEW_MOON -> Component.translatable(KEY + "_new_moon", clock);
            case NIGHT -> Component.translatable(KEY + "_night", clock,
                    Component.translatable(ChronometerTime.moonPhaseKey(moonPhase)),
                    ChronometerTime.nightsUntilNewMoon(moonPhase));
        };
        return line.withStyle(ChatFormatting.GRAY);
    }

    /**
     * The displayed tenth-of-a-day bucket for a rest-tick count — the granularity
     * {@link StatusFormat#awakeDays} renders to one decimal.
     */
    public static long awakeTenths(long ticksSinceRest) {
        return Math.round(ticksSinceRest / (double) TICKS_PER_TENTH);
    }

    /**
     * Whether the stack's stored days-awake should be rewritten: true only when the
     * live figure has moved by a displayed tenth of a day, so a held pocket
     * chronometer resyncs at most once per tenth-day rather than every tick.
     */
    public static boolean refreshDue(int storedTicks, long currentTicks) {
        return awakeTenths(storedTicks) != awakeTenths(currentTicks);
    }
}
