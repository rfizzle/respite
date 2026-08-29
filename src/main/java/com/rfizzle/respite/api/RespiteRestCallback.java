package com.rfizzle.respite.api;

import com.rfizzle.respite.Respite;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

/**
 * Callback fired when a player wakes at dawn having slept through the night.
 * Part of Respite's stable API surface (Concord API Standard v1).
 *
 * <p>Fired <strong>server-side only</strong>, once per night, at the moment the
 * player leaves the bed at dawn (the night, or a daytime thunderstorm, has
 * ended and vanilla wakes them). It fires only on a genuine dawn wake — an
 * interrupted sleep (waking to damage, or leaving the bed while it is still
 * night) does <em>not</em> fire it. It fires whether or not the player was
 * armed for Restful Saturation; an unarmed sleeper simply reports
 * {@code healthRestored} of 0.
 *
 * <p>A listener that throws is caught, logged, and skipped — it can never break
 * the dawn-wake dispatch (the rest-derived advancement criteria still grant) or
 * the listeners registered after it.
 */
@Stable
public interface RespiteRestCallback {

    /** One-shot gate so a listener that throws every night logs its stack trace once. */
    AtomicBoolean LISTENER_FAILURE_LOGGED = new AtomicBoolean(false);

    Event<RespiteRestCallback> EVENT = EventFactory.createArrayBacked(RespiteRestCallback.class,
            listeners -> (player, ticksSlept, healthRestored) -> {
                for (RespiteRestCallback listener : listeners) {
                    try {
                        listener.onPlayerRested(player, ticksSlept, healthRestored);
                    } catch (VirtualMachineError e) {
                        throw e; // OOME/SOE: the JVM is gone, not the guest
                    } catch (Throwable t) {
                        // Throwable, not Exception: a listener compiled against an older
                        // signature throws Error (AbstractMethodError, NoClassDefFoundError),
                        // which an Exception catch would let escape and kill the server tick.
                        if (LISTENER_FAILURE_LOGGED.compareAndSet(false, true)) {
                            Respite.LOGGER.warn("A RespiteRestCallback listener {} threw; skipping",
                                    listener.getClass().getName(), t);
                        }
                    }
                }
            });

    /**
     * Called after a player wakes at dawn having slept.
     *
     * @param player         the waking player
     * @param ticksSlept     world ticks the player slept this night (compressed
     *                       real-time under the time-lapse, but counted in world
     *                       ticks so the total is lapse-independent)
     * @param healthRestored health restored by Restful Saturation this night,
     *                       or 0 if the player was not armed
     */
    void onPlayerRested(ServerPlayer player, long ticksSlept, float healthRestored);
}
