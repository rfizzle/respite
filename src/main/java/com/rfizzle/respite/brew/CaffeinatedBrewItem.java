package com.rfizzle.respite.brew;

import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.registry.RespiteRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * The Caffeinated Brew ({@code design/SPEC.md} §6): a campfire-steeped drink
 * that lifts Weariness and grants a burst of Haste — one drink, never a potion.
 * Modeled on vanilla {@code PotionItem}/{@code HoneyBottleItem}: a potion-style
 * 32-tick {@link UseAnim#DRINK} animation (so the vanilla drink sound plays with
 * no override), consumed instantly on right-click regardless of hunger, and an
 * empty glass bottle returned in survival.
 *
 * <p>The zero-nutrition {@code alwaysEdible} food component carries the "not
 * food" contract (no hunger or saturation restored); the effects themselves are
 * applied server-side in {@link #finishUsingItem}, never through the food
 * component's own eat path. The drink behavior is deliberately <em>not</em>
 * gated on {@code enableCaffeinatedBrew} — that toggle removes the recipes, but
 * an already-brewed bottle must never brick (§6 Edge cases).
 */
public class CaffeinatedBrewItem extends Item {

    /** Potion-style drink length — 32 ticks (§6.3). */
    private static final int DRINK_TICKS = 32;

    public CaffeinatedBrewItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // Instant, hunger-independent drink start, exactly like a potion.
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return DRINK_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        // Server-authoritative: the client also calls this for use prediction, so
        // stat and effect mutation stay behind the isClientSide gate. A non-player
        // drinker (no stats) simply gets no brew effects.
        if (!level.isClientSide && entity instanceof ServerPlayer serverPlayer) {
            applyBrewEffects(serverPlayer);
        }

        Player player = entity instanceof Player p ? p : null;
        if (player != null) {
            player.awardStat(Stats.ITEM_USED.get(this));
            stack.consume(1, player);
        }
        // Return the emptied glass, mirroring the vanilla bottle-drink convention:
        // nothing back in creative, the bottle back (or in place) in survival.
        if (player == null || !player.hasInfiniteMaterials()) {
            if (stack.isEmpty()) {
                return new ItemStack(Items.GLASS_BOTTLE);
            }
            if (player != null) {
                player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
            }
        }
        entity.gameEvent(GameEvent.DRINK);
        return stack;
    }

    /**
     * The brew's payload (§6.3): clear both Weariness stages, reset the rest
     * clock so the sweep keeps them clear, and grant Haste I. Haste is added
     * through {@code addEffect}, so vanilla effect-merge governs an existing
     * beacon Haste or a re-drink — a longer or equal-strength Haste I never
     * escalates to Haste II.
     */
    private static void applyBrewEffects(ServerPlayer player) {
        player.removeEffect(RespiteRegistry.WEARY);
        player.removeEffect(RespiteRegistry.EXHAUSTED);
        player.getStats().setValue(player, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), 0);

        int hasteTicks = BrewMath.hasteDurationTicks(RespiteConfig.get().brewHasteSeconds);
        if (hasteTicks > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, hasteTicks, 0));
        }
    }
}
