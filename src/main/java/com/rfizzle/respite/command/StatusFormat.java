package com.rfizzle.respite.command;

import com.rfizzle.respite.weariness.WearinessMath;
import com.rfizzle.respite.weariness.WearinessStage;
import java.util.Locale;

/**
 * Pure formatting for {@code /respite status} ({@code design/SPEC.md}
 * §Commands): the time-awake figure and the rest-stage translation key. Kept
 * free of {@code net.minecraft} types so both unit-test without a Fabric
 * bootstrap; the command layer only wraps these in components.
 */
public final class StatusFormat {

    private StatusFormat() {
    }

    /** Time awake as days to one decimal, e.g. {@code "3.5"} ({@code Locale.ROOT} so the point never localizes to a comma). */
    public static String awakeDays(long ticksSinceRest) {
        return String.format(Locale.ROOT, "%.1f", ticksSinceRest / (double) WearinessMath.TICKS_PER_DAY);
    }

    /** The translation key naming a rest stage — routes through a key, never {@code Enum#name()}. */
    public static String restStageKey(WearinessStage stage) {
        return switch (stage) {
            case NONE -> "command.respite.status.stage.rested";
            case WEARY -> "command.respite.status.stage.weary";
            case EXHAUSTED -> "command.respite.status.stage.exhausted";
        };
    }
}
