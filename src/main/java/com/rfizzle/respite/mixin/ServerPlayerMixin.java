package com.rfizzle.respite.mixin;

import com.rfizzle.respite.restful.RestfulSleepHandler;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restful Saturation's world-tick seam ({@code design/SPEC.md} §2):
 * {@code doTick} is the body-timer tick that runs exactly once per world tick
 * a sleeper experiences — vanilla's connection handler calls it on real ticks,
 * the time-lapse engine's {@code tickSleepers} on extra ticks — so counting
 * its calls counts world ticks of sleep by construction. RETURN, not TAIL:
 * every completed call must count, whichever exit it takes. A dawn wake lands
 * inside the call, so {@code isSleeping} here is the settled state.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {

    @Inject(method = "doTick", at = @At("RETURN"))
    private void respite$restfulSleepTick(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.isSleeping()) {
            RestfulSleepHandler.onSleepingTick(self);
        }
    }
}
