package com.rfizzle.respite.timelapse;

import com.rfizzle.respite.Respite;
import com.rfizzle.respite.timelapse.TimeLapseTransitions.Cue;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: one effective-rate change ({@code design/SPEC.md} §1). The state picks
 * the action-bar line, the cue picks the start/settle sound; the client
 * honors {@code showTimeLapseMessages} for both. Sent only on change, never
 * per tick.
 */
public record TimeLapsePayload(LapseState state, Cue cue, int rate, int sleeping, int total)
        implements CustomPacketPayload {

    public static final Type<TimeLapsePayload> TYPE = new Type<>(Respite.id("time_lapse"));

    public static final StreamCodec<FriendlyByteBuf, TimeLapsePayload> CODEC =
            StreamCodec.of(TimeLapsePayload::encode, TimeLapsePayload::decode);

    private static void encode(FriendlyByteBuf buf, TimeLapsePayload payload) {
        buf.writeVarInt(payload.state.ordinal());
        buf.writeVarInt(payload.cue.ordinal());
        buf.writeVarInt(payload.rate);
        buf.writeVarInt(payload.sleeping);
        buf.writeVarInt(payload.total);
    }

    private static TimeLapsePayload decode(FriendlyByteBuf buf) {
        return new TimeLapsePayload(
                enumFromOrdinal(LapseState.values(), buf.readVarInt()),
                enumFromOrdinal(Cue.values(), buf.readVarInt()),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    /** Reject out-of-range ordinals instead of throwing an opaque AIOOBE. */
    private static <T extends Enum<T>> T enumFromOrdinal(T[] values, int ordinal) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new DecoderException("Unknown " + values[0].getDeclaringClass().getSimpleName()
                    + " ordinal: " + ordinal);
        }
        return values[ordinal];
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
