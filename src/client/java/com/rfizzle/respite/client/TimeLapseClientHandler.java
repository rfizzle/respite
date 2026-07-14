package com.rfizzle.respite.client;

import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.registry.RespiteRegistry;
import com.rfizzle.respite.timelapse.TimeLapseLines;
import com.rfizzle.respite.timelapse.TimeLapsePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

/**
 * Client side of the time-lapse announcements ({@code design/SPEC.md} §1):
 * draws the rate/hold/settle line on the action bar and plays the lapse-edge
 * cues as non-positional UI sounds — both behind the client's
 * {@code showTimeLapseMessages} toggle. Stateless: nothing to clear on
 * disconnect.
 */
public final class TimeLapseClientHandler {

    private TimeLapseClientHandler() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(TimeLapsePayload.TYPE, (payload, context) ->
                context.client().execute(() -> handle(context.client(), payload)));
    }

    private static void handle(Minecraft client, TimeLapsePayload payload) {
        // Feed the sky smoother first — it must track the live rate whether or not
        // the server announces or the client shows messages.
        TimeLapseSkySmoother.update(payload.state(), payload.rate());
        // The line and cue are gated by the server's announce flag and the client's
        // own toggle; the smoothing above is not.
        if (!payload.announce() || !RespiteConfig.get().showTimeLapseMessages) {
            return;
        }
        client.gui.setOverlayMessage(
                TimeLapseLines.build(payload.state(), payload.rate(), payload.sleeping(), payload.total()),
                false);
        switch (payload.cue()) {
            case START -> client.getSoundManager()
                    .play(SimpleSoundInstance.forUI(RespiteRegistry.TIME_LAPSE_START, 1.0f));
            case END -> client.getSoundManager()
                    .play(SimpleSoundInstance.forUI(RespiteRegistry.TIME_LAPSE_END, 1.0f));
            case NONE -> {
            }
        }
    }
}
