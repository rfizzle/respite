package com.rfizzle.respite.bedroll;

import com.rfizzle.respite.Respite;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: a bedroll sleep just began ({@code design/SPEC.md} §7) — the nudge that
 * opens the client's "Leave Bed" overlay. A bedroll sleep is started
 * server-side (never by the client interacting with a bed block), so the client
 * needs telling to open the screen; the payload carries no data.
 */
public record BedrollSleepPayload() implements CustomPacketPayload {

    public static final BedrollSleepPayload INSTANCE = new BedrollSleepPayload();

    public static final Type<BedrollSleepPayload> TYPE = new Type<>(Respite.id("bedroll_sleep"));

    public static final StreamCodec<FriendlyByteBuf, BedrollSleepPayload> CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
