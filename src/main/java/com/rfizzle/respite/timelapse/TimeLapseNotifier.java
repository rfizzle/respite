package com.rfizzle.respite.timelapse;

import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.registry.RespiteRegistry;
import com.rfizzle.respite.timelapse.TimeLapseTransitions.Announcement;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Sends each effective-rate change to Overworld players ({@code design/SPEC.md}
 * §1): a {@link TimeLapsePayload} to modded clients (which honor the client
 * toggle for line and cue alike), a plain action-bar line plus notify-sound to
 * clients without the mod. Gated server-side by {@code announceTimeLapse};
 * the transition machine still runs when the gate is off so a later toggle
 * never replays a stale cue.
 */
public final class TimeLapseNotifier {

    private static final TimeLapseTransitions TRANSITIONS = new TimeLapseTransitions();

    private TimeLapseNotifier() {
    }

    /** Feed one real tick's outcome; sends only when something changed. */
    static void announce(ServerLevel overworld, RespiteConfig config,
            LapseState state, int rate, int sleeping, int total) {
        Announcement announcement = TRANSITIONS.evaluate(state, rate, sleeping, total);
        if (announcement == null || !config.announceTimeLapse) {
            return;
        }
        TimeLapsePayload payload = new TimeLapsePayload(announcement.state(), announcement.cue(),
                announcement.rate(), announcement.sleeping(), announcement.total());
        SoundEvent cueSound = switch (announcement.cue()) {
            case START -> RespiteRegistry.TIME_LAPSE_START;
            case END -> RespiteRegistry.TIME_LAPSE_END;
            case NONE -> null;
        };
        List<ServerPlayer> players = overworld.players();
        for (int i = 0, size = players.size(); i < size; i++) {
            ServerPlayer player = players.get(i);
            if (ServerPlayNetworking.canSend(player, TimeLapsePayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            } else {
                // vanilla-client fallback: same line, same cue, no client toggle
                player.displayClientMessage(TimeLapseLines.build(announcement.state(),
                        announcement.rate(), announcement.sleeping(), announcement.total()), true);
                if (cueSound != null) {
                    player.playNotifySound(cueSound, SoundSource.MASTER, 1.0f, 1.0f);
                }
            }
        }
    }

    /** Server stopped — back to the fresh state. */
    static void reset() {
        TRANSITIONS.reset();
    }
}
