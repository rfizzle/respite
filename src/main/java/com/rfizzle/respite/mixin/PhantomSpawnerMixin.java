package com.rfizzle.respite.mixin;

import com.rfizzle.respite.config.RespiteConfig;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Suppresses vanilla insomnia phantom spawning while the rework is on
 * ({@code design/SPEC.md} §3): vanilla's {@link PhantomSpawner#tick} returns 0
 * before any of its work, so {@code TIME_SINCE_REST} never spawns a phantom.
 * Respite's own {@code RespitePhantomSpawner}, registered alongside in the same
 * custom-spawner list, takes over the when-and-where. With
 * {@code enablePhantomRework = false} this inject passes through and vanilla
 * insomnia runs untouched.
 */
@Mixin(PhantomSpawner.class)
abstract class PhantomSpawnerMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void respite$suppressInsomniaSpawns(CallbackInfoReturnable<Integer> cir) {
        if (RespiteConfig.get().enablePhantomRework) {
            cir.setReturnValue(0);
        }
    }
}
