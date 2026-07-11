package com.rfizzle.respite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rfizzle.respite.config.RespiteConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses vanilla's sleep resolution while the time-lapse is enabled
 * ({@code design/SPEC.md} §1): the "enough players sleeping → set time to
 * morning, wake everyone, clear weather" block never fires, and the
 * threshold-model announcements ({@code sleep.skipping_night},
 * {@code sleep.players_sleeping}) stay silent — Respite's rate line replaces
 * them. With {@code enableTimeLapse = false} both paths run untouched vanilla,
 * {@code playersSleepingPercentage} included.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {

    /**
     * Short-circuiting the skip block's first condition keeps the whole block
     * — {@code setDayTime}, {@code wakeUpAllPlayers}, {@code resetWeatherCycle}
     * — from running: players stay asleep in bed, weather rains itself out.
     */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/SleepStatus;areEnoughSleeping(I)Z"))
    private boolean respite$suppressVanillaSkip(SleepStatus sleepStatus, int percentage,
            Operation<Boolean> original) {
        if (RespiteConfig.get().enableTimeLapse) {
            return false;
        }
        return original.call(sleepStatus, percentage);
    }

    @Inject(method = "announceSleepStatus", at = @At("HEAD"), cancellable = true)
    private void respite$suppressSleepAnnouncement(CallbackInfo ci) {
        if (RespiteConfig.get().enableTimeLapse) {
            ci.cancel();
        }
    }
}
