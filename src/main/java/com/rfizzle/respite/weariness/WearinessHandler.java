package com.rfizzle.respite.weariness;

import com.rfizzle.respite.Respite;
import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.registry.RespiteRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
 * feature off pays nothing beyond that. Turning the feature off must leave
 * behaviorally-vanilla state, not a stuck indefinite marker: the effects are
 * reconciled to none on the enabled→disabled edge and again for any player who
 * logs in while the feature is off, so a marker never outlives the toggle. All
 * state is server-thread-only; the counter and the edge latch zero on
 * {@code SERVER_STOPPED}.
 */
public final class WearinessHandler {

    /** Half a second — the ≤5s milk-clear window is exactly one interval (§4.2). */
    static final int SWEEP_INTERVAL_TICKS = 100;

    private static int tickCounter;

    /**
     * The feature's state at the previous sweep, so the enabled→disabled
     * transition can strip lingering markers exactly once and a stably-disabled
     * server then pays nothing but the counter increment.
     */
    private static boolean wasEnabled;

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
        // A player logging in while the feature is off must not carry a marker
        // persisted from a session when it was on — reconcile them on join. With
        // the feature on this also settles the correct stage immediately, rather
        // than leaving it to the next sweep.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                sweepPlayer(handler.player, RespiteConfig.get());
            } catch (Exception e) {
                Respite.LOGGER.error("Weariness join reconcile failed for {}",
                        handler.player.getName().getString(), e);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            tickCounter = 0;
            wasEnabled = false;
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
            if (wasEnabled) {
                // Enabled→disabled edge: strip any lingering markers once, so an
                // online player mid-stage when the feature was switched off ends up
                // behaviorally vanilla. A stably-disabled server never reaches here.
                wasEnabled = false;
                sweepAll(server, config);
            }
            return;
        }
        wasEnabled = true;
        sweepAll(server, config);
    }

    private static void sweepAll(MinecraftServer server, RespiteConfig config) {
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
     * live sweep's per-player unit, and the seam gametests and the join reconcile
     * drive directly. Self-contained on the feature toggle: with the feature off
     * it reconciles to none (stripping any marker left over from when it was on),
     * so a toggle-off leaves behaviorally-vanilla state rather than a stuck
     * indefinite effect (§4). The tick loop still gates before the player scan, so
     * a stably-disabled server never calls this at all.
     */
    public static void sweepPlayer(ServerPlayer player, RespiteConfig config) {
        if (!config.enableWeariness) {
            applyStage(player, WearinessStage.NONE);
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
