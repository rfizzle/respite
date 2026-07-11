package com.rfizzle.respite.weariness;

import com.rfizzle.respite.Respite;
import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.registry.RespiteRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * The weariness sweep ({@code design/SPEC.md} §4): every {@value #SWEEP_INTERVAL_TICKS}
 * server ticks it reads each online player's {@code TIME_SINCE_REST} and applies
 * whichever stage matches the ladder, removing the other stage. Re-asserting the
 * matching effect every sweep is what caps an external clear (milk) at ≤5s: a
 * cleared effect is back within one interval, and the sweep is the only thing
 * enforcing "exactly one stage active", so the removal-of-the-other branch runs
 * unconditionally on every player.
 *
 * <p>Interval-gated so a server pays only a counter increment on the other
 * ninety-nine ticks, and gated on {@code enableWeariness} so a server with the
 * feature off pays nothing beyond that. All state is server-thread-only; the
 * counter zeroes on {@code SERVER_STOPPED}.
 */
public final class WearinessHandler {

    /** Half a second — the ≤5s milk-clear window is exactly one interval (§4.2). */
    static final int SWEEP_INTERVAL_TICKS = 100;

    private static int tickCounter;

    /**
     * This real tick's config snapshot, per {@code RespiteConfig.get()}'s rule.
     * The sweep only fires once every hundred ticks, but the snapshot keeps the
     * read off the volatile on the ninety-nine idle ticks and matches the
     * suite's tick-handler shape. Written on {@code START_SERVER_TICK}, read on
     * {@code END_SERVER_TICK}; server thread only.
     */
    private static RespiteConfig tickConfig;

    private WearinessHandler() {
    }

    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(server -> tickConfig = RespiteConfig.get());
        ServerTickEvents.END_SERVER_TICK.register(WearinessHandler::onEndTick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            tickCounter = 0;
            tickConfig = null;
        });
    }

    /** This real tick's snapshot; falls back to a live read before the first tick. */
    private static RespiteConfig config() {
        RespiteConfig local = tickConfig;
        return local != null ? local : RespiteConfig.get();
    }

    private static void onEndTick(MinecraftServer server) {
        if (++tickCounter < SWEEP_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        RespiteConfig config = config();
        if (!config.enableWeariness) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                sweepPlayer(player, config);
            } catch (Exception e) {
                Respite.LOGGER.error("Weariness sweep failed for {}", player.getName().getString(), e);
            }
        }
    }

    /**
     * Bring one player's effects in line with their {@code TIME_SINCE_REST}. The
     * live sweep's per-player unit, and the seam gametests drive directly — mock
     * players never enter the server player list, so the in-world tests apply the
     * stage through this call rather than the tick loop. Self-contained on the
     * feature toggle (the tick loop gates too, to skip the whole player scan): with
     * the feature off it applies nothing, so the effect is never asserted (§4).
     */
    public static void sweepPlayer(ServerPlayer player, RespiteConfig config) {
        if (!config.enableWeariness) {
            return;
        }
        long ticksSinceRest = player.getStats().getValue(Stats.CUSTOM, Stats.TIME_SINCE_REST);
        WearinessStage stage = WearinessMath.stageFor(
                ticksSinceRest, config.wearinessThresholdDays, config.exhaustedThresholdDays);
        applyStage(player, stage);
    }

    /**
     * Set the player's active stage to exactly {@code stage}: ensure the matching
     * effect is present and strip the other, so no frame ever shows both icons
     * (§4 Edge cases). Re-adds are idempotent — an effect already present is left
     * alone, so a settled stage costs one {@code hasEffect} check per sweep.
     */
    static void applyStage(ServerPlayer player, WearinessStage stage) {
        switch (stage) {
            case NONE -> {
                remove(player, RespiteRegistry.WEARY);
                remove(player, RespiteRegistry.EXHAUSTED);
            }
            case WEARY -> {
                remove(player, RespiteRegistry.EXHAUSTED);
                ensure(player, RespiteRegistry.WEARY);
            }
            case EXHAUSTED -> {
                remove(player, RespiteRegistry.WEARY);
                ensure(player, RespiteRegistry.EXHAUSTED);
            }
        }
    }

    private static void ensure(ServerPlayer player, Holder<MobEffect> effect) {
        if (!player.hasEffect(effect)) {
            // Indefinite, ambient, no particles, icon shown (§4.2): the effect is a
            // silent marker — the sweep re-asserts it, so duration is a formality.
            player.addEffect(new MobEffectInstance(
                    effect, MobEffectInstance.INFINITE_DURATION, 0, true, false, true));
        }
    }

    private static void remove(ServerPlayer player, Holder<MobEffect> effect) {
        if (player.hasEffect(effect)) {
            player.removeEffect(effect);
        }
    }
}
