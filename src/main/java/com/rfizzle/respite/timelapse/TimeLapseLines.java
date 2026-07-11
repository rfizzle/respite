package com.rfizzle.respite.timelapse;

import net.minecraft.network.chat.Component;

/**
 * The time-lapse action-bar lines ({@code design/SPEC.md} §Localization).
 * One {@link Component} shell shared by the client payload handler and the
 * server's fallback for clients without the mod, so both surfaces stay
 * identical by construction. The ✦ marker lives in the localized values.
 */
public final class TimeLapseLines {

    private TimeLapseLines() {
    }

    /** The line for a lapse state: the rate line, the peril hold, or the settle. */
    public static Component build(LapseState state, int rate, int sleeping, int total) {
        return switch (state) {
            case ACTIVE -> Component.translatable("notification.respite.time_lapse", rate, sleeping, total);
            case HELD -> Component.translatable("notification.respite.time_hold");
            case SETTLED -> Component.translatable("notification.respite.time_lapse_end");
        };
    }
}
