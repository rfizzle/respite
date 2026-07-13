package com.rfizzle.respite.mixin;

import com.rfizzle.respite.phantom.RespitePhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only access to {@link RespitePhantomSpawner}'s private {@code nextTick}
 * countdown, so the early-out gametests can prove each bail-out fires
 * <em>before</em> {@code --nextTick} rather than merely returning the same zero
 * an untouched spawner would — the Respite-side mirror of {@link
 * PhantomSpawnerAccessor}'s proof for vanilla.
 */
@Mixin(RespitePhantomSpawner.class)
public interface RespitePhantomSpawnerAccessor {

    @Accessor("nextTick")
    int respite$getNextTick();
}
