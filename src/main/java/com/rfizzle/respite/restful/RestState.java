package com.rfizzle.respite.restful;

/**
 * One armed sleeper's night ({@code design/SPEC.md} §2): the interval
 * counter, the ticks slept, the health restored, and whether any Deep Sleep
 * conversion ran. The ticks-slept and health-restored tallies are shaped to
 * serve the future {@code RespiteRestCallback} (§Public API) without shipping
 * it yet.
 *
 * <p>Mutated in place once per world tick slept — the per-tick path allocates
 * nothing. Plain fields, no {@code net.minecraft} types.
 */
public final class RestState {

    private long ticksSlept;
    private int intervalCounter;
    private float healthRestored;
    private boolean deepConversionRan;

    RestState() {
    }

    /**
     * One world tick slept; true exactly when a conversion step falls due.
     * The interval is read per tick, so a config reload mid-sleep takes
     * effect at the next boundary.
     */
    public boolean tickAndCheckDue(int intervalTicks) {
        ticksSlept++;
        if (++intervalCounter >= intervalTicks) {
            intervalCounter = 0;
            return true;
        }
        return false;
    }

    /** A conversion step healed {@code healed} health; deep marks Deep Sleep. */
    public void recordConversion(float healed, boolean deep) {
        healthRestored += healed;
        if (deep) {
            deepConversionRan = true;
        }
    }

    /** World ticks this player has slept since arming. */
    public long ticksSlept() {
        return ticksSlept;
    }

    /** Health actually restored this night (post-clamp deltas, not attempts). */
    public float healthRestored() {
        return healthRestored;
    }

    /** True once any conversion ran under the new moon. */
    public boolean deepConversionRan() {
        return deepConversionRan;
    }
}
