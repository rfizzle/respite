package com.rfizzle.respite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.registry.RespiteRegistry;
import com.rfizzle.respite.weariness.WearinessMath;
import com.rfizzle.respite.weariness.WearinessStage;
import com.rfizzle.respite.wellrested.WellRestedMath;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The two-pole regen scaling on food-based natural regeneration ({@code design/SPEC.md}
 * §4): while a player is Weary or Exhausted it heals {@code × (1 − penalty)}, and
 * while Well-Rested it heals {@code × (1 + bonus)}. {@code FoodData#tick} has two
 * natural-regen heal call sites — the food≥20 saturated fast regen and the food≥18
 * slow regen — and both resolve to the same {@code Player#heal(F)} target, so one
 * un-ordinal'd wrap matches both call sites and scales every natural-regen heal and
 * nothing else: instant health, the Regeneration effect, beacon and peaceful regen,
 * and Restful Saturation's conversion all heal through other call sites and are
 * untouched.
 *
 * <p>The two poles compose multiplicatively rather than branch, so neither silently
 * drops the other on the rare occasion both are present (an op forcing
 * {@code TIME_SINCE_REST} back over threshold while the grace still ticks); under
 * normal play a freshly-woken player is never Weary, so exactly one pole is ever
 * active. Each pole is independently config-gated.
 *
 * <p>{@code @WrapOperation} (not {@code @Redirect}) so the wrap composes with
 * other mods that touch these calls, and so the {@code Player} is in scope to
 * resolve the active effects. State is read off the player's live effects (the
 * synced source of truth), not re-derived from the stat. Disjoint from
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
        return respite$wearinessFactor(player, config) * respite$wellRestedFactor(player, config);
    }

    private static float respite$wearinessFactor(Player player, RespiteConfig config) {
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

    private static float respite$wellRestedFactor(Player player, RespiteConfig config) {
        if (!config.enableWellRested) {
            return 1.0f;
        }
        return WellRestedMath.regenFactor(
                player.hasEffect(RespiteRegistry.WELL_RESTED), config.wellRestedRegenBonus);
    }
}
