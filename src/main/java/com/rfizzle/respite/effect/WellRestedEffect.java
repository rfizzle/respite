package com.rfizzle.respite.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * The Well-Rested grace ({@code design/SPEC.md} §4): the positive pole of the
 * weariness ladder, granted on a genuine dawn wake. Like {@link WearinessEffect}
 * it is a behaviour-free marker — the natural-regen bonus lives in the
 * {@code FoodData#tick} regen hook, not in the effect — so the effect itself only
 * carries a category and a tint colour for its icon. {@code BENEFICIAL} drives the
 * green potion-bar background for free. The {@code MobEffect} constructor is
 * {@code protected}, so this thin subclass exists only to expose it to
 * {@code RespiteRegistry}.
 */
public final class WellRestedEffect extends MobEffect {

    public WellRestedEffect(int color) {
        super(MobEffectCategory.BENEFICIAL, color);
    }
}
