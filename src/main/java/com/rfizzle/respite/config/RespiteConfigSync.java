package com.rfizzle.respite.config;

import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of the config sync ({@code design/SPEC.md} §Configuration). The
 * server pushes its active config to each client on join and again on
 * {@code /respite reload}, so a connected client's gameplay reads follow the
 * server's authoritative rules rather than its own local file. Send-guarded on
 * the client actually having the receiver, so a vanilla or older client is a
 * silent no-op.
 *
 * <p>Registers the S2C payload type — which runs in common init, so both sides
 * know the type — while the receiver lives in client code.
 */
public final class RespiteConfigSync {

    private RespiteConfigSync() {
    }

    public static void register() {
        // S2C only; the type must be known on both sides, the receiver is client wiring.
        PayloadTypeRegistry.playS2C().register(RespiteConfigPayload.TYPE, RespiteConfigPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendTo(handler.player));
    }

    /** Push the server's current config to one player, if their client can receive it. */
    public static void sendTo(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, RespiteConfigPayload.TYPE)) {
            ServerPlayNetworking.send(player, RespiteConfigPayload.of(RespiteConfig.get()));
        }
    }

    /** Re-push the server's config to every connected player — the {@code /respite reload} seam. */
    public static void broadcast(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (int i = 0, size = players.size(); i < size; i++) {
            sendTo(players.get(i));
        }
    }
}
