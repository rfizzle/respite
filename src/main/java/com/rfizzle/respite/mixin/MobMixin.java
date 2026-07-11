package com.rfizzle.respite.mixin;

import com.rfizzle.respite.timelapse.TimeLapseEngine;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Peril-brake tracking ({@code design/SPEC.md} §1): every hostile target
 * change flows through {@link Mob#setTarget}, so the tracker maintains a
 * per-player count of hostiles hunting them with no entity scan at rate
 * evaluation. {@code Mob} is the narrowest class declaring the method;
 * the {@code Enemy} marker check keeps non-hostiles out at one
 * {@code instanceof} per target change (rare, AI-driven — not per-tick).
 */
@Mixin(Mob.class)
abstract class MobMixin {

    @Inject(method = "setTarget", at = @At("HEAD"))
    private void respite$trackPerilTarget(LivingEntity target, CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (!(self instanceof Enemy) || self.level().isClientSide) {
            return;
        }
        TimeLapseEngine.onMobTargetChanged(self, target);
    }
}
