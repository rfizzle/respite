package com.rfizzle.respite.rest;

import com.rfizzle.respite.advancement.RespiteCriteria;
import com.rfizzle.respite.api.RespiteRestCallback;
import com.rfizzle.respite.restful.RestfulMath;
import net.minecraft.server.level.ServerPlayer;

/**
 * The dawn-wake dispatcher ({@code design/SPEC.md} §Public API + §Advancements):
 * the one place a genuine dawn wake fans out to the public
 * {@link RespiteRestCallback} and the rest-derived advancement criteria (root,
 * beauty_sleep, dark_and_dreamless). It is fed by
 * {@code RestfulSleepHandler}'s wake seam, which decides <em>when</em> a wake is
 * a dawn wake and gathers the facts; this class decides <em>what</em> a dawn
 * wake grants.
 *
 * <p>Distinct from the {@code restful} package: that owns §2's overnight
 * healing conversion, this owns the wake-derived events those and other systems
 * expose. Kept as one small pure-ish seam so a gametest can drive it directly
 * with a mock player and controlled facts, per the {@code mc-advancements}
 * grant-assertion pattern. No feature toggles are checked here — each grant's
 * fact is already gated at its source (the lapse only runs when enabled; health
 * is only restored when Restful Saturation is on), so a disabled feature simply
 * never presents the fact.
 */
public final class RestWakeEvents {

    /** Hearts (as health points) restored in one night that earns Beauty Sleep — 8 hearts. */
    public static final float BEAUTY_SLEEP_HEALTH = 16.0f;

    private RestWakeEvents() {
    }

    /**
     * Whether a wake is a genuine dawn wake: the level is day (vanilla only
     * auto-wakes sleepers once night ends — a damage or manual wake happens
     * while it is still night) and the player actually slept. Pure so the gate
     * unit-tests without a level.
     */
    public static boolean isDawnWake(boolean isDay, long ticksSlept) {
        return isDay && ticksSlept > 0;
    }

    /**
     * Whether the time-lapse touched a sleep: it was last active at or after
     * the real tick the sleep began (and has been active at all — the marker is
     * -1 when it never has). Pure so the crossing unit-tests without the engine.
     */
    public static boolean lapseTouchedSleep(long lastActiveRealTick, long sleepStartRealTick) {
        return lastActiveRealTick >= 0 && lastActiveRealTick >= sleepStartRealTick;
    }

    /**
     * A player woke at dawn having slept. Fires the public rest callback, then
     * the criteria whose facts are satisfied.
     *
     * @param player         the waking player
     * @param ticksSlept     world ticks slept this night
     * @param healthRestored health restored by Restful Saturation this night
     * @param lapseActive    the time-lapse was active at some point during the sleep
     * @param nightMoonPhase the vanilla moon phase of the night that was slept
     */
    public static void onDawnWake(ServerPlayer player, long ticksSlept, float healthRestored,
            boolean lapseActive, int nightMoonPhase) {
        RespiteRestCallback.EVENT.invoker().onPlayerRested(player, ticksSlept, healthRestored);

        if (lapseActive) {
            RespiteCriteria.SLEPT_THROUGH_LAPSE.trigger(player);
        }
        if (healthRestored >= BEAUTY_SLEEP_HEALTH) {
            RespiteCriteria.BEAUTY_SLEEP.trigger(player);
        }
        if (nightMoonPhase == RestfulMath.NEW_MOON_PHASE) {
            RespiteCriteria.DARK_AND_DREAMLESS.trigger(player);
        }
    }
}
