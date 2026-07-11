package com.rfizzle.respite.restful;

import com.rfizzle.respite.Respite;
import com.rfizzle.respite.config.RespiteConfig;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

/**
 * Restful Saturation ({@code design/SPEC.md} §2): going to bed on a full
 * stomach converts saturation into overnight healing. Arming happens on the
 * sleep-start event; the conversion rides {@code ServerPlayer#doTick} — which
 * runs exactly once per world tick a sleeper experiences (vanilla's connection
 * handler on real ticks, the time-lapse engine's {@code tickSleepers} on extra
 * ticks) — so the interval counts world ticks by construction and the lapse
 * compresses the wait without changing the totals.
 *
 * <p>While the feature is on, a sleeping player's vanilla food tick stands
 * down entirely (the {@code FoodData#tick} head-cancel): no food-based natural
 * regeneration (the conversion replaces it, §2.4), no exhaustion processing,
 * and no starvation ticking — the hunger bar is frozen in bed and only the
 * conversion spends saturation (§2.5). {@code Player#causeFoodExhaustion} is
 * cancelled while sleeping for the same reason: the Hunger status effect calls
 * it directly every tick, sleeping or not, and would otherwise bank exhaustion
 * against the moment of waking.
 *
 * <p>Wake accounting fires on the sleep-stop event, which covers every wake
 * path (dawn, damage, leaving the bed) but never disconnect — the tracker is
 * therefore also cleared per player on {@code DISCONNECT}, and wholesale on
 * {@code SERVER_STOPPED}. All state is server-thread-only and transient.
 */
public final class RestfulSleepHandler {

    private static final RestfulTracker TRACKER = new RestfulTracker();

    /**
     * The config snapshot for this real tick, per {@code RespiteConfig.get()}'s
     * rule for repeated-tick code: the hot paths below run once per world tick
     * per sleeper (up to 60× per real tick under the lapse) and must not
     * re-invoke the volatile {@code get()} inside that loop. Written once per
     * real tick and read only on the server thread — every consumer of
     * {@link #config()} sits behind a server-side gate.
     */
    private static RespiteConfig tickConfig;

    private RestfulSleepHandler() {
    }

    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(server -> tickConfig = RespiteConfig.get());
        EntitySleepEvents.START_SLEEPING.register(RestfulSleepHandler::onStartSleeping);
        EntitySleepEvents.STOP_SLEEPING.register(RestfulSleepHandler::onStopSleeping);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                TRACKER.forget(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            TRACKER.clear();
            tickConfig = null;
        });
    }

    /** This real tick's snapshot; falls back to a live read before the first tick. */
    private static RespiteConfig config() {
        RespiteConfig local = tickConfig;
        return local != null ? local : RespiteConfig.get();
    }

    /** Arming (§2.1) — evaluated at the moment sleep starts, server-side. */
    private static void onStartSleeping(LivingEntity entity, BlockPos sleepingPos) {
        // The Fabric hook fires on both logical sides; only the server player counts.
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        RespiteConfig config = config();
        if (!config.enableRestfulSaturation) {
            return;
        }
        try {
            if (RestfulMath.arms(player.getFoodData().getFoodLevel(), config.restfulRequiresFullHunger)) {
                TRACKER.arm(player.getUUID());
            }
        } catch (Exception e) {
            Respite.LOGGER.error("Restful arming failed for {}", player.getName().getString(), e);
        }
    }

    /** Wake accounting and feedback (§2.7) — fires on every wake path. */
    private static void onStopSleeping(LivingEntity entity, BlockPos sleepingPos) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        RestState state = TRACKER.forget(player.getUUID());
        if (state == null || !config().enableRestfulSaturation) {
            return;
        }
        try {
            String key = RestfulMath.wakeLineKey(state.healthRestored(), state.deepConversionRan());
            if (key != null) {
                player.displayClientMessage(Component.translatable(key), true);
            }
        } catch (Exception e) {
            Respite.LOGGER.error("Restful wake feedback failed for {}", player.getName().getString(), e);
        }
    }

    /**
     * One world tick a sleeping player experienced — the {@code doTick} mixin's
     * seam, hot during a time-lapse (up to 60× per real tick per sleeper):
     * a config snapshot, one map read, one counter bump on the common path.
     */
    public static void onSleepingTick(ServerPlayer player) {
        RespiteConfig config = config();
        if (!config.enableRestfulSaturation) {
            return;
        }
        RestState state = TRACKER.get(player.getUUID());
        if (state == null) {
            return; // not armed
        }
        try {
            if (state.tickAndCheckDue(config.restfulHealIntervalTicks)) {
                convert(player, state, config);
            }
        } catch (Exception e) {
            Respite.LOGGER.error("Restful conversion failed for {}", player.getName().getString(), e);
        }
    }

    /** One due conversion step (§2.2–3, stop conditions re-checked per step). */
    private static void convert(ServerPlayer player, RestState state, RespiteConfig config) {
        FoodData food = player.getFoodData();
        float saturation = food.getSaturationLevel();
        float healthBefore = player.getHealth();
        if (!RestfulMath.stepAllowed(saturation, healthBefore, player.getMaxHealth())) {
            return;
        }
        int moonPhase = player.serverLevel().getMoonPhase();
        // Vanilla setters, so other mods' hooks observe both mutations.
        food.setSaturation(saturation - RestfulMath.SATURATION_COST_PER_STEP);
        player.heal(RestfulMath.healPerStep(moonPhase, config.newMoonHealMultiplier));
        // Tally the post-clamp delta, not the attempt — heal() clamps at max
        // health and other mods may scale or cancel it.
        float healed = player.getHealth() - healthBefore;
        if (healed > 0.0f) {
            state.recordConversion(healed, moonPhase == RestfulMath.NEW_MOON_PHASE);
        }
    }

    /**
     * True while the vanilla food tick and exhaustion intake must stand down
     * for this player (§2.4–5) — the two food mixins' shared gate, server-side
     * only. Sleeping is the cheap first check; the config read is this real
     * tick's snapshot. Deliberately keyed on sleeping, not on being armed: an
     * unarmed sleeper's regen also stands down, because sleepers receive full
     * time-lapse extra ticks and world-tick-cadence vanilla regen across an
     * accelerated night would dwarf the conversion — suspension is what keeps
     * the full supper the best overnight strategy.
     */
    public static boolean suspendsFoodTick(Player player) {
        return player.isSleeping() && config().enableRestfulSaturation;
    }
}
