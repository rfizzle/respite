package com.rfizzle.respite.client;

import com.rfizzle.respite.timelapse.LapseState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.GameRules;

/**
 * Client-side sky smoothing for the time-lapse ({@code design/SPEC.md} §1).
 * During an accelerated night the server advances the world clock by the
 * effective rate each real tick and broadcasts the honest time every tick, but
 * the client's own clock only creeps forward one tick at a time between those
 * packets — so the sun visibly snaps forward whenever a packet lands. This
 * advances the client's {@code dayTime} at the live effective rate each client
 * tick, so the client is already where the next server packet will place it and
 * the sky glides instead of stuttering.
 *
 * <p>The rate comes from {@link com.rfizzle.respite.timelapse.TimeLapsePayload}
 * on every effective-rate edge, fed in through {@link #update}. Purely cosmetic:
 * the server remains authoritative for the clock (its per-tick time packet
 * re-anchors the client each real tick); this only smooths the motion between
 * those anchors. Client-thread only.
 */
public final class TimeLapseSkySmoother {

    /** Effective rate of the active lapse; 1 when no lapse is accelerating. */
    private static volatile int rate = 1;

    private TimeLapseSkySmoother() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(TimeLapseSkySmoother::onClientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> rate = 1);
    }

    /**
     * Feed the latest lapse edge from the network. Only an {@code ACTIVE} lapse
     * accelerates the clock; a held (peril brake) or settled lapse runs the sky
     * at normal speed.
     */
    public static void update(LapseState state, int effectiveRate) {
        rate = state == LapseState.ACTIVE ? Math.max(1, effectiveRate) : 1;
    }

    private static void onClientTick(Minecraft client) {
        int r = rate;
        if (r <= 1) {
            return;
        }
        ClientLevel level = client.level;
        if (level == null || !level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            return;
        }
        // Vanilla's client tick already advanced dayTime by 1 this tick; add the
        // remaining (rate - 1) so the net advance matches the server's per-tick
        // acceleration. The server's ClientboundSetTimePacket re-anchors each real
        // tick, so any drift from client/server tick jitter is corrected at once.
        level.setDayTime(level.getDayTime() + (r - 1L));
    }
}
