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
}
