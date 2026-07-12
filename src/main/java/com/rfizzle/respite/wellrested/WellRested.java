package com.rfizzle.respite.wellrested;

import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.registry.RespiteRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * The Well-Rested grant ({@code design/SPEC.md} §4): the positive pole of the
 * weariness ladder. Applying the {@code respite:well_rested} effect on a genuine
 * dawn wake is the whole of it — the regen bonus itself lives in the
 * {@code FoodData#tick} hook and reads the effect off the player, so this class
 * only decides <em>whether</em> and <em>how long</em>.
 *
 * <p>Gated here on {@code enableWellRested}, not in {@code RestWakeEvents}: that
 * class is deliberately toggle-free so a gametest can drive it with controlled
 * facts, so the config check lives with {@code RestfulSleepHandler}'s per-tick
 * config snapshot instead. A genuine dawn wake is the only caller — an
 * interrupted wake never reaches the grant, and the Caffeinated Brew's rest
 * reset is not a sleep and never passes through here — so drinking a brew never
 * grants the grace (a brew postpones rest, it is not a night's sleep).
 */
public final class WellRested {

    private WellRested() {
    }

    /**
     * Grant the Well-Rested grace to a player who just woke at dawn having slept.
     * A no-op while the feature is off or the duration is zero; otherwise applies
     * (or refreshes) the effect for the configured duration. Ambient with no
     * particles and its icon shown — a silent, self-expiring marker, so no sweep
     * re-asserts it (unlike the weariness stages, which persist while over
     * threshold). The icon rides vanilla's effect sync; nothing is networked.
     */
    public static void grantOnDawnWake(ServerPlayer player, RespiteConfig config) {
        if (!config.enableWellRested || config.wellRestedSeconds <= 0) {
            return;
        }
        player.addEffect(new MobEffectInstance(
                RespiteRegistry.WELL_RESTED,
                WellRestedMath.durationTicks(config.wellRestedSeconds),
                0, true, false, true));
    }
}
