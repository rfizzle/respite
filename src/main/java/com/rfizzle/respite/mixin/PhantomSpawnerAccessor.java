package com.rfizzle.respite.mixin;

import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only access to vanilla {@link PhantomSpawner}'s private {@code nextTick}
 * countdown, so the suppression gametest can prove the inject fires <em>before</em>
 * vanilla advances its own state rather than merely returning the same zero
 * vanilla would.
 */
@Mixin(PhantomSpawner.class)
public interface PhantomSpawnerAccessor {

    @Accessor("nextTick")
    int respite$getNextTick();
}
