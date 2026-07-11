package com.rfizzle.respite.mixin;

import com.rfizzle.respite.restful.RestfulSleepHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restful Saturation's regen suspension ({@code design/SPEC.md} §2.4–5):
 * while a player sleeps with the feature on, the whole vanilla food tick
 * stands down — both natural-regen branches (the conversion replaces them),
 * exhaustion processing, and starvation ticking — so the hunger bar is frozen
 * in bed and only the conversion spends saturation. Suppressing starvation is
 * a stated side effect, unreachable for an armed sleeper behind the ≥18-food
 * arming gate. {@code tick} is called only server-side (guarded in
 * {@code Player#tick}), and resumes untouched on wake.
 */
@Mixin(FoodData.class)
abstract class FoodDataMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void respite$suspendWhileSleeping(Player player, CallbackInfo ci) {
        if (RestfulSleepHandler.suspendsFoodTick(player)) {
            ci.cancel();
        }
    }
}
