package com.rfizzle.respite.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * The status effect behind a weariness stage ({@code design/SPEC.md} §4): a
 * neutral, behaviour-free marker. Both {@code respite:weary} and
 * {@code respite:exhausted} are instances of this class — the healing penalty
 * lives in the {@code FoodData#tick} regen hook, not in the effect, so the
 * effect itself only carries a category and a tint colour for its icon. The
 * {@code MobEffect} constructor is {@code protected}, so this thin subclass
 * exists only to expose it to {@code RespiteRegistry}.
 */
public final class WearinessEffect extends MobEffect {

    public WearinessEffect(int color) {
        super(MobEffectCategory.NEUTRAL, color);
    }
}
