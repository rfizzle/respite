package com.rfizzle.respite.restful;

/**
 * The restful-saturation arithmetic ({@code design/SPEC.md} §2): the arming
 * gate, the per-step stop conditions, the Deep Sleep heal amount, and the
 * wake-feedback line choice. One formula, one home — the sleep handler,
 * the tests, and any later surface all read from here.
 *
 * <p>Deliberately free of {@code net.minecraft} types so the full conversion
 * accounting unit-tests without a Fabric bootstrap.
 */
public final class RestfulMath {

    /** Moon phase index of the new moon — Deep Sleep's key (§2.3). */
    public static final int NEW_MOON_PHASE = 4;

    /** Saturation spent by one conversion step (§2.2). */
    public static final float SATURATION_COST_PER_STEP = 1.0f;

    /** Health healed by one ordinary conversion step (§2.2). */
    public static final float BASE_HEAL_PER_STEP = 1.0f;

    /** Health a night must restore before the wake line shows — 3 hearts (§2.7). */
    public static final float NOTIFY_HEALTH_THRESHOLD = 6.0f;

    /** The full hunger bar the strict arming gate requires (§2.1). */
    public static final int FULL_HUNGER = 20;

    /** The relaxed gate's floor — vanilla's natural-regen threshold (§2.1). */
    public static final int RELAXED_HUNGER_FLOOR = 18;

    private RestfulMath() {
    }

    /**
     * The arming gate, evaluated at the moment sleep starts: a full hunger
     * bar, or food ≥ 18 when {@code restfulRequiresFullHunger} is relaxed.
     */
    public static boolean arms(int foodLevel, boolean requiresFullHunger) {
        return foodLevel >= (requiresFullHunger ? FULL_HUNGER : RELAXED_HUNGER_FLOOR);
    }

    /**
     * True when a due conversion step may run. The stop conditions
     * (saturation floor, full health) are re-checked per step, never latched —
     * poison mid-sleep drops health back below max and conversion resumes
     * at the next due step (§2.6, §Edge cases).
     */
    public static boolean stepAllowed(float saturation, float health, float maxHealth) {
        return saturation >= SATURATION_COST_PER_STEP && health < maxHealth;
    }

    /**
     * Health healed by one conversion step for the same 1.0 saturation:
     * the base amount, or {@code × newMoonHealMultiplier} under Deep Sleep.
     */
    public static float healPerStep(int moonPhase, double newMoonHealMultiplier) {
        return moonPhase == NEW_MOON_PHASE
                ? (float) (BASE_HEAL_PER_STEP * newMoonHealMultiplier)
                : BASE_HEAL_PER_STEP;
    }

    /**
     * The wake action-bar line's translation key, or null when the night's
     * healing stayed under the three-heart threshold. Any Deep Sleep
     * conversion upgrades the line (§2.7).
     */
    public static String wakeLineKey(float healthRestored, boolean deepConversionRan) {
        if (healthRestored < NOTIFY_HEALTH_THRESHOLD) {
            return null;
        }
        return deepConversionRan ? "notification.respite.deep_rested" : "notification.respite.rested";
    }
}
