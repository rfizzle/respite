package com.rfizzle.respite.advancement;

import com.rfizzle.respite.Respite;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Respite's custom advancement criteria ({@code design/SPEC.md} §Advancements):
 * the four grants whose condition no vanilla predicate can express, each a
 * plain {@link PlayerTrigger} (vanilla's no-condition "this player did the
 * thing" trigger) registered under a Respite id and fired from the mod's own
 * already-gated success path. The other two advancements (mountain_watch,
 * clockwork) ride vanilla triggers straight from their JSON and need nothing
 * here.
 *
 * <p>Registration must run in {@code onInitialize}, <em>before</em> any
 * advancement JSON referencing these ids is deserialized on datapack load — an
 * unknown trigger id fails the whole advancement. {@link #register()} is
 * idempotent because gametest and (future) datagen bootstrap reach it too, and
 * a duplicate {@link Registry#register} throws.
 */
public final class RespiteCriteria {

    /** Slept through a night with the time-lapse active (advancement respite:root). */
    public static final PlayerTrigger SLEPT_THROUGH_LAPSE = new PlayerTrigger();

    /** Woke with enough hearts restored by Restful Saturation (respite:beauty_sleep). */
    public static final PlayerTrigger BEAUTY_SLEEP = new PlayerTrigger();

    /** Woke at dawn from a night slept through a new moon (respite:dark_and_dreamless). */
    public static final PlayerTrigger DARK_AND_DREAMLESS = new PlayerTrigger();

    /** Drank a Caffeinated Brew while carrying a Weariness stage (respite:night_shift). */
    public static final PlayerTrigger NIGHT_SHIFT = new PlayerTrigger();

    private static boolean registered;

    private RespiteCriteria() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Respite.id("slept_through_lapse"), SLEPT_THROUGH_LAPSE);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Respite.id("beauty_sleep"), BEAUTY_SLEEP);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Respite.id("dark_and_dreamless"), DARK_AND_DREAMLESS);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Respite.id("night_shift"), NIGHT_SHIFT);
    }
}
