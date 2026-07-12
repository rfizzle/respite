package com.rfizzle.respite.sleepvote;

import com.rfizzle.respite.Respite;
import com.rfizzle.respite.config.RespiteConfig;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The sleep whisper ({@code design/SPEC.md} §1, Sleep whisper): with vanilla's
 * night skip retired, "who's sleeping?" loses its running tally, so this pushes
 * one quiet chat line to the sleeper's level on every bed enter and leave —
 * {@code Alex is in bed (2/4)} — restoring the negotiation the shared night
 * turns on.
 *
 * <p>Rides {@link EntitySleepEvents} — the same seam Restful Saturation and the
 * Bedroll use. It is stateless: the {@code sleeping}/{@code total} share is
 * recomputed fresh from the live player list at each event, so a disconnect
 * (which fires no leave event) needs no cleanup — the departed player is simply
 * gone from the next scan. Server-thread-only; nothing to reset on stop.
 *
 * <p>Two shapes keep the line honest. On a <em>leave</em>, vanilla has not yet
 * cleared the leaving player's sleeping state when the event fires (Fabric
 * injects at the head of {@code stopSleeping}), so the count explicitly excludes
 * the actor — otherwise every "left the bed" line would report one sleeper too
 * many. And a leave line is sent only while sleep is still eligible (night or a
 * thunderstorm): the whole crew auto-waking at dawn is the night ending, not a
 * negotiation, and needs no burst of "left the bed" lines.
 */
public final class SleepVoteHandler {

    private SleepVoteHandler() {
    }

    public static void register() {
        EntitySleepEvents.START_SLEEPING.register((entity, pos) -> onSleepTransition(entity, true));
        EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> onSleepTransition(entity, false));
    }

    private static void onSleepTransition(LivingEntity entity, boolean enter) {
        // The Fabric hook fires on both logical sides; only the server player counts.
        if (!(entity instanceof ServerPlayer actor)) {
            return;
        }
        RespiteConfig config = RespiteConfig.get();
        if (!config.announceSleepVote) {
            // Feature off: no scan, no send — behaviorally untouched vanilla.
            return;
        }
        try {
            whisper(actor, enter);
        } catch (Exception e) {
            Respite.LOGGER.error("Sleep whisper failed for {}", actor.getName().getString(), e);
        }
    }

    private static void whisper(ServerPlayer actor, boolean enter) {
        ServerLevel level = actor.serverLevel();

        // A leave once the night is over (dawn, or a thunderstorm blowing out) is
        // the crew waking together, not someone opting out — stay silent so the
        // morning isn't a stack of "left the bed" lines.
        if (!enter && !(level.isNight() || level.isThundering())) {
            return;
        }

        int total = 0;
        int sleeping = 0;
        List<ServerPlayer> players = level.players();
        for (int i = 0, size = players.size(); i < size; i++) {
            ServerPlayer player = players.get(i);
            if (player.isSpectator()) {
                continue;
            }
            total++;
            // On a leave the actor is still isSleeping() here; the whisper reports
            // the share after they rise, so it is never counted among the sleepers.
            if (!enter && player == actor) {
                continue;
            }
            if (player.isSleeping()) {
                sleeping++;
            }
        }

        // A solo world has no negotiation to narrate — the whisper is a
        // multiplayer signal, so a lone occupant sees nothing.
        if (total < 2) {
            return;
        }

        Component line = SleepVoteLines.build(enter, actor.getDisplayName(), sleeping, total);
        for (int i = 0, size = players.size(); i < size; i++) {
            players.get(i).sendSystemMessage(line);
        }
    }
}
