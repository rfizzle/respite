package com.rfizzle.respite.config;

import com.rfizzle.respite.Respite;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: the server's active config, pushed to a client on join and re-sent on
 * {@code /respite reload} ({@code design/SPEC.md} §Configuration). Gameplay keys
 * are server-authoritative, so a connected client honors the server's rules
 * rather than its own local file; the client-only presentation toggles are read
 * from the client's own config regardless.
 *
 * <p>The config travels as its canonical JSON — the same shape
 * {@link RespiteConfig} serializes to disk — so a new field costs nothing here:
 * the payload never drifts from the config's field set. The JSON is already at
 * {@link ConfigMigrator#CURRENT_VERSION}, so the receiver deserializes without
 * migrating and clamps defensively.
 */
public record RespiteConfigPayload(String json) implements CustomPacketPayload {

    /** Bounds the on-wire string so a hostile or corrupt frame can't allocate unboundedly. */
    private static final int MAX_JSON_BYTES = 16 * 1024;

    public static final Type<RespiteConfigPayload> TYPE = new Type<>(Respite.id("config_sync"));

    public static final StreamCodec<FriendlyByteBuf, RespiteConfigPayload> CODEC =
            StreamCodec.of(RespiteConfigPayload::encode, RespiteConfigPayload::decode);

    private static void encode(FriendlyByteBuf buf, RespiteConfigPayload payload) {
        buf.writeUtf(payload.json, MAX_JSON_BYTES);
    }

    private static RespiteConfigPayload decode(FriendlyByteBuf buf) {
        return new RespiteConfigPayload(buf.readUtf(MAX_JSON_BYTES));
    }

    /** The payload carrying {@code config}'s current JSON. */
    public static RespiteConfigPayload of(RespiteConfig config) {
        return new RespiteConfigPayload(RespiteConfig.GSON.toJson(config));
    }

    /**
     * Deserialize the synced config. Already current-schema (built from a live
     * config, not read from disk), so no migration runs; clamped defensively so a
     * malformed frame can never seat out-of-range gameplay values on the client.
     * Falls back to defaults if the JSON is unusable.
     */
    public RespiteConfig toConfig() {
        RespiteConfig config;
        try {
            config = RespiteConfig.GSON.fromJson(json, RespiteConfig.class);
        } catch (Exception e) {
            Respite.LOGGER.warn("Malformed synced config from server; using defaults", e);
            config = null;
        }
        if (config == null) {
            config = new RespiteConfig();
        }
        config.clamp();
        return config;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
