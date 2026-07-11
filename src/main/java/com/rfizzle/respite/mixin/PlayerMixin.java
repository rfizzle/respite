package com.rfizzle.respite.mixin;

import com.rfizzle.respite.restful.RestfulSleepHandler;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restful Saturation's exhaustion freeze ({@code design/SPEC.md} §2.5): food
 * exhaustion never accrues in bed. The food-tick suspension alone can't
 * guarantee that — the Hunger status effect calls {@code causeFoodExhaustion}
 * directly every tick, sleeping or not, and would bank exhaustion against the
 * moment of waking. {@code Player} is the class declaring the method; the
 * client early-out keeps the cancel server-authoritative (vanilla's method is
 * already a client no-op) and keeps the handler's per-tick config snapshot
 * server-thread-only.
 */
@Mixin(Player.class)
abstract class PlayerMixin {

    @Inject(method = "causeFoodExhaustion", at = @At("HEAD"), cancellable = true)
    private void respite$noExhaustionInBed(float exhaustion, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide) {
            return;
        }
        if (RestfulSleepHandler.suspendsFoodTick(self)) {
            ci.cancel();
        }
    }
}
