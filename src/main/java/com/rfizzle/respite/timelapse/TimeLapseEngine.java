package com.rfizzle.respite.timelapse;

import com.rfizzle.respite.Respite;
import com.rfizzle.respite.config.RespiteConfig;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.Util;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

/**
 * The continuous time-lapse ({@code design/SPEC.md} §1): once per <em>real</em>
 * server tick, compute the target rate from the sleeping share, apply the
 * peril brake, then run up to {@code rate − 1} additional full Overworld ticks
 * under the wall-clock budget. {@code dayTime} is never set directly — every
 * extra tick is a genuine {@link ServerLevel#tick} the whole world experiences.
 *
 * <p>Vanilla splits player ticking: the level's entity loop runs only
 * {@link ServerPlayer#tick()} (housekeeping), while the body-timer chain —
 * food, effect durations, air, fire, natural-regen cadence — runs through
 * {@link ServerPlayer#doTick()} from the connection handler, at real cadence.
 * So awake players keep real time in their own body for free, and the engine
 * grants sleeping players their full extra ticks by calling {@code doTick()}
 * for them once per extra tick.
 *
 * <p>All state is server-thread-only and transient: reset on server stop,
 * never persisted. A restart forgets an in-flight lapse; the next real tick
 * re-derives it from who is in bed.
 */
public final class TimeLapseEngine {

    /** Real ticks a peril signal holds the brake after the fight ends (5 s). */
    public static final int PERIL_WINDOW_TICKS = 100;

    /** What the lapse is doing this real tick. */
    public enum LapseState {
        /** Running extra ticks — the effective rate is above 1. */
        ACTIVE,
        /** Sleepers would accelerate, but an awake player's peril holds time at ×1. */
        HELD,
        /** No lapse: nobody sleeping, not night, or the feature idle. */
        SETTLED,
    }

    private static final BooleanSupplier HAS_TIME = () -> true;
    private static final PerilTracker PERIL = new PerilTracker();

    private static long realTickCount;
    private static long tickStartNanos;
    private static long rateEvaluations;
    private static boolean extraTicksInProgress;
    private static int effectiveRate = 1;
    private static LapseState state = LapseState.SETTLED;
    private static int sleeping;
    private static int total;

    private TimeLapseEngine() {
    }

    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(server -> tickStartNanos = Util.getNanos());
        ServerTickEvents.END_SERVER_TICK.register(TimeLapseEngine::onEndServerTick);
        ServerLivingEntityEvents.AFTER_DAMAGE.register(TimeLapseEngine::onAfterDamage);
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof Mob) {
                PERIL.onMobRemoved(entity.getUUID());
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                PERIL.forgetPlayer(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());
    }

    private static void onEndServerTick(MinecraftServer server) {
        realTickCount++;
        RespiteConfig config = RespiteConfig.get();
        if (!config.enableTimeLapse) {
            // Vanilla parity while off: no evaluation, no residue from before the toggle.
            if (!PERIL.isEmpty()) {
                PERIL.clear();
            }
            effectiveRate = 1;
            state = LapseState.SETTLED;
            return;
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }
        try {
            evaluateAndRun(overworld, config);
        } catch (Exception e) {
            Respite.LOGGER.error("Time-lapse evaluation failed; settling to ×1", e);
            effectiveRate = 1;
            state = LapseState.SETTLED;
        }
    }

    private static void evaluateAndRun(ServerLevel overworld, RespiteConfig config) {
        if (extraTicksInProgress) {
            // Recursion guard: rate evaluation runs only on real ticks. Extra
            // ticks fire world-tick events but never server-tick events, so
            // this cannot trip — it stays as the contract's cheap insurance.
            return;
        }
        rateEvaluations++;

        List<ServerPlayer> players = overworld.players();
        int n = 0;
        int k = 0;
        for (int i = 0, size = players.size(); i < size; i++) {
            ServerPlayer player = players.get(i);
            if (player.isSpectator()) {
                continue;
            }
            n++;
            if (player.isSleeping()) {
                k++;
            }
        }

        boolean eligible = overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)
                && (overworld.isNight() || overworld.isThundering());
        int target = eligible ? TimeLapseMath.targetRate(config.maxTimeLapseRate, k, n) : 1;

        boolean held = false;
        if (target > 1 && config.combatHoldsTime) {
            for (int i = 0, size = players.size(); i < size; i++) {
                ServerPlayer player = players.get(i);
                if (player.isSpectator() || player.isSleeping()) {
                    continue;
                }
                if (PERIL.isInPeril(player.getUUID(), realTickCount, PERIL_WINDOW_TICKS)) {
                    held = true;
                    break;
                }
            }
        }

        int extras = 0;
        if (target > 1 && !held) {
            long budgetNanos = config.timeLapseTickBudgetMs * 1_000_000L;
            // A base tick already over budget runs zero extras — a struggling
            // server degrades to whatever pace it can afford, never a stall.
            if (Util.getNanos() - tickStartNanos < budgetNanos) {
                long extrasStart = Util.getNanos();
                extraTicksInProgress = true;
                try {
                    while (extras < target - 1 && Util.getNanos() - extrasStart < budgetNanos) {
                        overworld.tick(HAS_TIME);
                        tickSleepers(overworld);
                        extras++;
                    }
                } finally {
                    extraTicksInProgress = false;
                }
            }
        }

        effectiveRate = 1 + extras;
        sleeping = k;
        total = n;
        state = effectiveRate > 1 ? LapseState.ACTIVE : held ? LapseState.HELD : LapseState.SETTLED;

        if (extras > 0) {
            // Vanilla syncs time every 20 real ticks — at 60× the sky would
            // lurch ~1,200 ticks per jump. Feed clients the honest clock every
            // real tick while accelerating; the sky stays vanilla rendering.
            overworld.getServer().getPlayerList().broadcastAll(
                    new ClientboundSetTimePacket(overworld.getGameTime(), overworld.getDayTime(),
                            overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)),
                    overworld.dimension());
        }
    }

    /**
     * Sleeping players receive the full extra tick in their own body (§2's
     * overnight conversion counts on it): the body-timer chain vanilla runs
     * from the connection handler at real cadence is repeated here for them.
     */
    private static void tickSleepers(ServerLevel overworld) {
        List<ServerPlayer> players = overworld.players();
        // Indexed walk with the size re-read each pass: a death mid-doTick may
        // shrink the list; at worst one sleeper skips one extra tick.
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            if (player.isSleeping() && !player.isSpectator()) {
                player.doTick();
            }
        }
    }

    /** Mixin seam: a hostile mob's attack target changed. */
    public static void onMobTargetChanged(Mob mob, LivingEntity target) {
        RespiteConfig config = RespiteConfig.get();
        if (!config.enableTimeLapse || !config.combatHoldsTime) {
            return;
        }
        UUID playerId = target instanceof Player player && !player.isSpectator() ? player.getUUID() : null;
        PERIL.onMobTarget(mob.getUUID(), playerId);
    }

    private static void onAfterDamage(LivingEntity entity, DamageSource source,
            float baseDamage, float damageTaken, boolean blocked) {
        RespiteConfig config = RespiteConfig.get();
        if (!config.enableTimeLapse || !config.combatHoldsTime) {
            return;
        }
        if (entity instanceof ServerPlayer victim && !victim.isSpectator()) {
            PERIL.recordCombat(victim.getUUID(), realTickCount);
        }
        if (source.getEntity() instanceof ServerPlayer attacker && !attacker.isSpectator()) {
            PERIL.recordCombat(attacker.getUUID(), realTickCount);
        }
    }

    private static void reset() {
        realTickCount = 0;
        tickStartNanos = 0;
        rateEvaluations = 0;
        extraTicksInProgress = false;
        effectiveRate = 1;
        state = LapseState.SETTLED;
        sleeping = 0;
        total = 0;
        PERIL.clear();
    }

    /** The effective rate this real tick — extras actually run, plus one. */
    public static int getEffectiveRate() {
        return effectiveRate;
    }

    /** What the lapse is doing this real tick. */
    public static LapseState getState() {
        return state;
    }

    /** Sleepers counted at the last evaluation. */
    public static int getSleeping() {
        return sleeping;
    }

    /** Non-spectator Overworld players counted at the last evaluation. */
    public static int getTotal() {
        return total;
    }

    /** True while the engine is inside one of its own extra ticks. */
    public static boolean isExtraTickInProgress() {
        return extraTicksInProgress;
    }

    /** Real server ticks seen since server start — the peril clock. */
    public static long getRealTickCount() {
        return realTickCount;
    }

    /** Rate evaluations run — equals the real tick count while enabled (recursion-guard contract). */
    public static long getRateEvaluations() {
        return rateEvaluations;
    }
}
