package com.rfizzle.respite.client;

import com.rfizzle.respite.bedroll.BedrollSleepPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.InBedChatScreen;

/**
 * Client side of the bedroll ({@code design/SPEC.md} §7): opens the vanilla
 * "Leave Bed" overlay when the server starts a bedroll sleep. A bedroll sleep
 * is server-initiated (the client used an item, not a bed block), so the screen
 * that a normal bed opens client-side needs this nudge. Stateless.
 */
public final class BedrollSleepClientHandler {

    private BedrollSleepClientHandler() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(BedrollSleepPayload.TYPE, (payload, context) ->
                context.client().execute(() -> context.client().setScreen(new InBedChatScreen())));
    }
}
