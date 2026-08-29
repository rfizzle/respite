package com.rfizzle.respite.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the Concord API Standard's listener-isolation contract on both public
 * callbacks: a throwing listener is caught and skipped, the listeners
 * registered after it still run, and a {@link VirtualMachineError} is rethrown
 * rather than absorbed.
 *
 * <p>Fabric {@code Event}s have no unregister, so registrations here are
 * permanent for the test JVM — one method per callback, asserting in order, so
 * the fatal-error listener cannot leak into the isolation assertion. No other
 * unit test invokes either callback.
 */
class CallbackIsolationTest {

    @Test
    void restCallbackIsolatesListenersButRethrowsFatalErrors() {
        List<String> ran = new ArrayList<>();

        RespiteRestCallback.EVENT.register((player, ticksSlept, healthRestored) -> ran.add("first"));
        RespiteRestCallback.EVENT.register((player, ticksSlept, healthRestored) -> {
            throw new IllegalStateException("guest is broken");
        });
        // AbstractMethodError is what a consumer compiled against an older signature
        // raises — an Exception catch would let it escape and kill the server tick.
        RespiteRestCallback.EVENT.register((player, ticksSlept, healthRestored) -> {
            throw new AbstractMethodError("stale consumer");
        });
        RespiteRestCallback.EVENT.register((player, ticksSlept, healthRestored) -> ran.add("last"));

        assertDoesNotThrow(() -> RespiteRestCallback.EVENT.invoker().onPlayerRested(null, 100L, 4.0f));
        assertEquals(List.of("first", "last"), ran);

        RespiteRestCallback.EVENT.register((player, ticksSlept, healthRestored) -> {
            throw new StackOverflowError("the JVM is gone, not the guest");
        });
        assertThrows(StackOverflowError.class,
                () -> RespiteRestCallback.EVENT.invoker().onPlayerRested(null, 1L, 0.0f));
    }

    @Test
    void timeLapseCallbackIsolatesListenersButRethrowsFatalErrors() {
        List<String> ran = new ArrayList<>();

        RespiteTimeLapseCallback.EVENT.register((level, oldRate, newRate, sleeping, total) -> ran.add("first"));
        RespiteTimeLapseCallback.EVENT.register((level, oldRate, newRate, sleeping, total) -> {
            throw new IllegalStateException("guest is broken");
        });
        RespiteTimeLapseCallback.EVENT.register((level, oldRate, newRate, sleeping, total) -> {
            throw new NoClassDefFoundError("stale consumer");
        });
        RespiteTimeLapseCallback.EVENT.register((level, oldRate, newRate, sleeping, total) -> ran.add("last"));

        assertDoesNotThrow(() -> RespiteTimeLapseCallback.EVENT.invoker().onRateChanged(null, 1, 8, 1, 2));
        assertEquals(List.of("first", "last"), ran);

        RespiteTimeLapseCallback.EVENT.register((level, oldRate, newRate, sleeping, total) -> {
            throw new StackOverflowError("the JVM is gone, not the guest");
        });
        assertThrows(StackOverflowError.class,
                () -> RespiteTimeLapseCallback.EVENT.invoker().onRateChanged(null, 8, 1, 0, 2));
    }
}
