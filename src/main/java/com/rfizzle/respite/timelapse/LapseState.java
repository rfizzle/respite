package com.rfizzle.respite.timelapse;

/**
 * What the time-lapse is doing on a given real tick ({@code design/SPEC.md}
 * §1). Plain enum, no {@code net.minecraft} types: the transition logic and
 * the notification payload both ride on it, and both unit-test without a
 * Fabric bootstrap.
 */
public enum LapseState {
    /** Running extra ticks — the effective rate is above 1. */
    ACTIVE,
    /** Sleepers would accelerate, but an awake player's peril holds time at ×1. */
    HELD,
    /** No lapse: nobody sleeping, not night, or the feature idle. */
    SETTLED,
}
