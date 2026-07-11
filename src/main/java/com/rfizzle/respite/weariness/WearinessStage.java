package com.rfizzle.respite.weariness;

/**
 * The sleeplessness ladder ({@code design/SPEC.md} §4): exactly one stage is
 * active at a time. {@link #NONE} is rested; {@link #WEARY} and {@link #EXHAUSTED}
 * are the two debuff stages, each backed by its own status effect and its own
 * regen penalty. Ordered by severity so {@link #WEARY} sits below
 * {@link #EXHAUSTED}.
 */
public enum WearinessStage {
    NONE,
    WEARY,
    EXHAUSTED
}
