package com.rfizzle.respite.timelapse;

/**
 * The time-lapse rate formula ({@code design/SPEC.md} §1): with {@code k} of
 * {@code n} Overworld players asleep, the target rate is
 * {@code max(1, round(maxTimeLapseRate × k / n))}. One formula, one home —
 * the engine, {@code /respite status} (later), and the public API (later) all
 * read from here.
 *
 * <p>Deliberately free of {@code net.minecraft} types so the full k/n sweep
 * unit-tests without a Fabric bootstrap.
 */
public final class TimeLapseMath {

    private TimeLapseMath() {
    }

    /**
     * The target rate before the performance governor and the peril brake:
     * {@code max(1, round(maxRate × sleeping / total))}. No sleepers, no
     * players, or a non-positive max all yield 1 — the time-lapse inactive.
     */
    public static int targetRate(int maxRate, int sleeping, int total) {
        if (sleeping <= 0 || total <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.round(maxRate * (double) sleeping / total));
    }

    /**
     * Whether a player is idle and so counts for nothing on either side of the
     * k/n share ({@code design/SPEC.md} §1, Idle exclusion): idle when the
     * exclusion is enabled and no input has arrived for at least
     * {@code thresholdMinutes} of real time. {@code nowMillis} and
     * {@code lastActionMillis} are the vanilla real-time idle signal
     * ({@link net.minecraft.server.level.ServerPlayer#getLastActionTime()}, a
     * monotonic {@code Util.getMillis()} stamp refreshed on every input packet —
     * the same signal {@code player-idle-timeout} uses), so a returning player
     * rejoins the moment they move or interact.
     *
     * <p>Disabled exclusion, a non-positive threshold, or a not-yet-elapsed (or
     * clock-skewed negative) gap all read as not idle — the strict, count-everyone
     * behavior.
     */
    public static boolean isIdle(boolean enabled, long nowMillis, long lastActionMillis, int thresholdMinutes) {
        if (!enabled || thresholdMinutes <= 0) {
            return false;
        }
        return nowMillis - lastActionMillis >= thresholdMinutes * 60_000L;
    }
}
