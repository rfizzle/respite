package com.rfizzle.respite.client;

import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.config.RespiteConfigPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side holder for the server's synced config ({@code design/SPEC.md}
 * §Configuration). Set when the server pushes its config on join or reload,
 * cleared on disconnect so the next singleplayer world falls back to the local
 * file. Client code that must honor a server-authoritative rule reads
 * {@link #serverConfig()} first and treats {@code null} as "not connected to a
 * server that sent one" — the offline/standalone fallback to {@code RespiteConfig.get()}.
 *
 * <p>The client-only presentation toggles ({@code showTimeLapseMessages},
 * {@code showExhaustionBlink}) are never taken from here — they stay the
 * client's own, read from {@code RespiteConfig.get()}.
 */
public final class ClientRespiteConfig {

    private static volatile @Nullable RespiteConfig serverConfig;

    private ClientRespiteConfig() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(RespiteConfigPayload.TYPE, (payload, context) -> {
            RespiteConfig synced = payload.toConfig();
            context.client().execute(() -> serverConfig = synced);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> serverConfig = null);
    }

    /** The server's synced config, or {@code null} when not connected to one that sent it. */
    public static @Nullable RespiteConfig serverConfig() {
        return serverConfig;
    }
}
