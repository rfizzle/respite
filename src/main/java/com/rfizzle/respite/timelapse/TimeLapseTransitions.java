package com.rfizzle.respite.timelapse;

/**
 * The announcement state machine ({@code design/SPEC.md} §1 + §Sound Design):
 * which action-bar line and which cue each effective-rate change earns. The
 * start cue fires when a lapse episode audibly opens and the settle cue when
 * it closes; the peril brake swaps lines <em>silently</em> — HELD neither
 * closes an episode nor re-fires the start cue when the fight ends, and
 * mid-lapse rate changes update the line only.
 *
 * <p>Pure and instance-scoped (no {@code net.minecraft} types), so every
 * transition path unit-tests without a Fabric bootstrap.
 */
public final class TimeLapseTransitions {

    /** The sound half of an announcement. */
    public enum Cue {
        NONE,
        /** The lapse audibly opens — {@code respite:ui.time_lapse.start}. */
        START,
        /** The lapse audibly closes — {@code respite:ui.time_lapse.end}. */
        END,
    }

    /** One announcement: the state picks the line, the cue picks the sound. */
    public record Announcement(LapseState state, Cue cue, int rate, int sleeping, int total) {
    }

    private LapseState lastState = LapseState.SETTLED;
    private int lastRate = 1;
    private int lastSleeping;
    private int lastTotal;
    private boolean episodeOpen;

    /**
     * Feed one real tick's outcome; returns the announcement it earns, or
     * null when nothing the player can see changed. Must be called every real
     * tick regardless of announce toggles, so the machine never misses a
     * transition — callers gate the <em>send</em>, not the evaluation.
     */
    public Announcement evaluate(LapseState state, int rate, int sleeping, int total) {
        boolean changed = state != lastState
                || (state == LapseState.ACTIVE
                        && (rate != lastRate || sleeping != lastSleeping || total != lastTotal));
        lastState = state;
        lastRate = rate;
        lastSleeping = sleeping;
        lastTotal = total;
        if (!changed) {
            return null;
        }
        Cue cue = Cue.NONE;
        if (state == LapseState.ACTIVE && !episodeOpen) {
            cue = Cue.START;
            episodeOpen = true;
        } else if (state == LapseState.SETTLED && episodeOpen) {
            cue = Cue.END;
            episodeOpen = false;
        }
        return new Announcement(state, cue, rate, sleeping, total);
    }

    /** Back to the fresh-server state. */
    public void reset() {
        lastState = LapseState.SETTLED;
        lastRate = 1;
        lastSleeping = 0;
        lastTotal = 0;
        episodeOpen = false;
    }
}
