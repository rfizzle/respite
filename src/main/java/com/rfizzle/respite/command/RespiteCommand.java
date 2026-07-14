package com.rfizzle.respite.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.rfizzle.respite.Respite;
import com.rfizzle.respite.block.ChronometerBlock;
import com.rfizzle.respite.chronometer.ChronometerTime;
import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.config.RespiteConfigSync;
import com.rfizzle.respite.timelapse.LapseState;
import com.rfizzle.respite.timelapse.TimeLapseEngine;
import com.rfizzle.respite.weariness.WearinessHandler;
import com.rfizzle.respite.weariness.WearinessMath;
import com.rfizzle.respite.weariness.WearinessStage;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The {@code /respite} command tree ({@code design/SPEC.md} §Commands): a
 * perm-0 self {@code status} read, and the op-gated (perm 2) {@code reload} and
 * {@code rest} testing levers. Player-facing output routes through
 * {@code command.respite.*} translation keys; the one literal line is
 * {@code reload}'s op-only list of changed config fields (the sanctioned dense-
 * diagnostic exception, DESIGN-SYSTEM §10). Every mutation routes through the
 * same seam gameplay uses — the stat setter plus {@link WearinessHandler#sweepPlayer}
 * — so the Weariness ladder reacts at once rather than at the next sweep.
 */
public final class RespiteCommand {

    /** Block reach for the "looking at a Chronometer" pick — generous, block-only. */
    private static final double LOOK_REACH = 6.0;

    /** Upper bound on {@code /respite rest set &lt;days&gt;} — bounds the stat write; days × 24000 stays well inside int. */
    private static final int MAX_REST_DAYS = 1000;

    private RespiteCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("respite")
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> reload(ctx.getSource())))
                .then(Commands.literal("rest")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("clear")
                                .executes(ctx -> restClearSelf(ctx.getSource()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> restClear(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("days", IntegerArgumentType.integer(0, MAX_REST_DAYS))
                                        .executes(ctx -> restSetSelf(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "days")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> restSet(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "days"))))))));
    }

    // --- status (perm 0, self) ---

    private static int status(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.respite.not_player"));
            return 0;
        }
        MinecraftServer server = source.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);

        // Time-lapse line — the Overworld's lapse, the only place it runs.
        LapseState state = TimeLapseEngine.getState();
        Component lapseLine = switch (state) {
            case HELD -> Component.translatable("command.respite.status.time_held");
            case ACTIVE -> Component.translatable("command.respite.status.time_active",
                    TimeLapseEngine.getEffectiveRate(), TimeLapseEngine.getSleeping(), TimeLapseEngine.getTotal());
            case SETTLED -> Component.translatable("command.respite.status.time_settled");
        };
        source.sendSuccess(() -> lapseLine, false);

        // Time awake + rest stage.
        RespiteConfig config = RespiteConfig.get();
        long ticksSinceRest = player.getStats().getValue(Stats.CUSTOM, Stats.TIME_SINCE_REST);
        WearinessStage stage = WearinessMath.stageFor(
                ticksSinceRest, config.wearinessThresholdDays, config.exhaustedThresholdDays);
        String awake = StatusFormat.awakeDays(ticksSinceRest);
        source.sendSuccess(() -> Component.translatable("command.respite.status.awake",
                awake, Component.translatable(StatusFormat.restStageKey(stage))), false);

        // Nights until the next new moon (the Overworld moon owns the clock).
        int moonPhase = overworld != null ? overworld.getMoonPhase() : player.serverLevel().getMoonPhase();
        int nights = ChronometerTime.nightsUntilNewMoon(moonPhase);
        Component moonLine = nights == 0
                ? Component.translatable("command.respite.status.new_moon_tonight")
                : Component.translatable("command.respite.status.new_moon", nights);
        source.sendSuccess(() -> moonLine, false);

        // A Chronometer the caller is looking at, if any — the signal it emits.
        HitResult hit = player.pick(LOOK_REACH, 1.0f, false);
        if (hit instanceof BlockHitResult blockHit) {
            BlockState looked = player.level().getBlockState(blockHit.getBlockPos());
            if (looked.getBlock() instanceof ChronometerBlock) {
                int signal = looked.getValue(ChronometerBlock.POWER);
                source.sendSuccess(() -> Component.translatable("command.respite.status.chronometer", signal), false);
            }
        }
        return 1;
    }

    // --- reload (perm 2) ---

    private static int reload(CommandSourceStack source) {
        RespiteConfig before = RespiteConfig.get();
        List<String> changed;
        try {
            RespiteConfig.reload();
            changed = ConfigDiff.changedFields(before, RespiteConfig.get());
        } catch (Exception e) {
            Respite.LOGGER.error("Config reload failed via command", e);
            source.sendFailure(Component.translatable("command.respite.reload_failed",
                    String.valueOf(e.getMessage())));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.respite.reload", changed.size()), true);
        if (!changed.isEmpty()) {
            // Op telemetry: the exact changed-field list, literal (sanctioned dense-diagnostic exception).
            source.sendSuccess(() -> Component.literal(String.join(", ", changed)), false);
        }
        // Push the reloaded config to every connected client so their gameplay
        // reads and config screen follow the server's new authoritative values.
        RespiteConfigSync.broadcast(source.getServer());
        // Re-evaluate the config-bound resource conditions (recipe/advancement
        // feature gates load at datapack time), mirroring vanilla /reload.
        MinecraftServer server = source.getServer();
        server.reloadResources(server.getPackRepository().getSelectedIds()).exceptionally(t -> {
            Respite.LOGGER.error("Resource reload after /respite reload failed", t);
            source.sendFailure(Component.translatable("command.respite.reload_recipes_failed"));
            return null;
        });
        return 1;
    }

    // --- rest (perm 2) ---

    private static int restClearSelf(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.respite.not_player"));
            return 0;
        }
        return restClear(source, player);
    }

    private static int restClear(CommandSourceStack source, ServerPlayer target) {
        setRest(target, 0);
        source.sendSuccess(() -> Component.translatable("command.respite.rest.clear",
                target.getDisplayName()), true);
        return 1;
    }

    private static int restSetSelf(CommandSourceStack source, int days) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.respite.not_player"));
            return 0;
        }
        return restSet(source, player, days);
    }

    private static int restSet(CommandSourceStack source, ServerPlayer target, int days) {
        setRest(target, days * (int) WearinessMath.TICKS_PER_DAY);
        source.sendSuccess(() -> Component.translatable("command.respite.rest.set",
                target.getDisplayName(), days), true);
        return 1;
    }

    /**
     * Write the target's {@code TIME_SINCE_REST} and reconcile the Weariness
     * ladder now — the same setter gameplay uses, then the same per-player sweep
     * the join reconcile runs, so the stage reacts within the command rather
     * than at the next 100-tick sweep.
     */
    private static void setRest(ServerPlayer target, int ticks) {
        target.getStats().setValue(target, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), ticks);
        WearinessHandler.sweepPlayer(target, RespiteConfig.get());
    }
}
