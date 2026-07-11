package com.rfizzle.respite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.registry.RespiteRegistry;
import com.rfizzle.respite.weariness.WearinessMath;
import com.rfizzle.respite.weariness.WearinessStage;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Weariness's healing penalty ({@code design/SPEC.md} §4.3): while a player is
 * Weary or Exhausted, food-based natural regeneration heals {@code × (1 − penalty)}.
 * {@code FoodData#tick} has two natural-regen heal call sites — the food≥20
 * saturated fast regen and the food≥18 slow regen — and both resolve to the same
 * {@code Player#heal(F)} target, so one un-ordinal'd wrap matches both call sites
 * and scales every natural-regen heal and nothing else: instant health, the
 * Regeneration effect, beacon and peaceful regen, and Restful Saturation's
 * conversion all heal through other call sites and are untouched.
 *
 * <p>{@code @WrapOperation} (not {@code @Redirect}) so the wrap composes with
 * other mods that touch these calls, and so the {@code Player} is in scope to
 * resolve the active stage. The stage is read off the player's live effects (the
 * sweep's synced source of truth), not re-derived from the stat. Disjoint from
 * {@link FoodDataMixin}'s HEAD-cancel, which fires only while sleeping and never
 * reaches this call site.
 */
@Mixin(FoodData.class)
abstract class FoodDataRegenMixin {

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;heal(F)V"))
    private void respite$scaleNaturalRegen(Player player, float amount, Operation<Void> original) {
        original.call(player, amount * respite$regenFactor(player));
    }

    private static float respite$regenFactor(Player player) {
        RespiteConfig config = RespiteConfig.get();
        if (!config.enableWeariness) {
            return 1.0f;
        }
        WearinessStage stage;
        if (player.hasEffect(RespiteRegistry.EXHAUSTED)) {
            stage = WearinessStage.EXHAUSTED;
        } else if (player.hasEffect(RespiteRegistry.WEARY)) {
            stage = WearinessStage.WEARY;
        } else {
            return 1.0f;
        }
        return WearinessMath.regenFactor(stage, config.wearinessRegenPenalty, config.exhaustedRegenPenalty);
    }
}
