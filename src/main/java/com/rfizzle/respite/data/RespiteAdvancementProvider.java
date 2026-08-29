package com.rfizzle.respite.data;

import com.rfizzle.respite.Respite;
import com.rfizzle.respite.advancement.RespiteCriteria;
import com.rfizzle.respite.registry.RespiteRegistry;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.ItemUsedOnLocationTrigger;
import net.minecraft.advancements.critereon.KilledTrigger;
import net.minecraft.advancements.critereon.LocationPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Respite's advancement tab ({@code design/SPEC.md} §Advancements). The root
 * grants on sleeping through a time lapse; five children hang off it — four on
 * Respite's own {@link PlayerTrigger}s ({@code beauty_sleep},
 * {@code dark_and_dreamless}, {@code night_shift}, and the root's own
 * {@code slept_through_lapse}) and two on vanilla triggers that need no custom
 * criterion ({@code clockwork} on placing the Chronometer, {@code mountain_watch}
 * on a high-altitude phantom kill).
 *
 * <p>The five recipe-unlock advancements under {@code advancement/recipes/} are
 * <em>not</em> written here — Minecraft derives them from the recipes in
 * {@link RespiteRecipeProvider}, feature gate included.
 */
public class RespiteAdvancementProvider extends FabricAdvancementProvider {

    private static final ResourceLocation ADVENTURE_BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/advancements/backgrounds/adventure.png");

    /** Mountain Watch's altitude floor — one block above the vanilla cloud layer. */
    private static final double MOUNTAIN_WATCH_MIN_Y = 101.0;

    protected RespiteAdvancementProvider(FabricDataOutput output,
                                         CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(output, registryLookup);
    }

    @Override
    public void generateAdvancement(HolderLookup.Provider registryLookup,
                                    Consumer<AdvancementHolder> consumer) {
        RespiteRegistry.register();
        RespiteCriteria.register();

        AdvancementHolder root = builder()
                .display(Items.RED_BED,
                        title("root"), description("root"),
                        ADVENTURE_BACKGROUND, AdvancementType.TASK, true, false, false)
                .addCriterion("slept_through_lapse", playerDid(RespiteCriteria.SLEPT_THROUGH_LAPSE))
                .save(consumer, Respite.id("root").toString());

        child(consumer, root, "beauty_sleep", Items.GOLDEN_APPLE, AdvancementType.GOAL,
                "beauty_sleep", playerDid(RespiteCriteria.BEAUTY_SLEEP));

        child(consumer, root, "dark_and_dreamless", Items.ENCHANTED_GOLDEN_APPLE, AdvancementType.GOAL,
                "dark_and_dreamless", playerDid(RespiteCriteria.DARK_AND_DREAMLESS));

        child(consumer, root, "night_shift", RespiteRegistry.CAFFEINATED_BREW, AdvancementType.TASK,
                "night_shift", playerDid(RespiteCriteria.NIGHT_SHIFT));

        child(consumer, root, "clockwork", RespiteRegistry.CHRONOMETER, AdvancementType.TASK,
                "place_chronometer",
                ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(RespiteRegistry.CHRONOMETER));

        child(consumer, root, "mountain_watch", Items.PHANTOM_MEMBRANE, AdvancementType.TASK,
                "phantom_kill_high", phantomKilledAboveTheClouds());
    }

    /** One child of the root, with the tab's shared toast/chat/hidden posture. */
    private static void child(Consumer<AdvancementHolder> consumer,
                              AdvancementHolder root,
                              String name,
                              ItemLike icon,
                              AdvancementType type,
                              String criterionName,
                              Criterion<?> criterion) {
        builder()
                .parent(root)
                .display(icon, title(name), description(name), null, type, true, false, false)
                .addCriterion(criterionName, criterion)
                .save(consumer, Respite.id(name).toString());
    }

    /**
     * A builder with telemetry off.
     *
     * <p>{@link Advancement.Builder#advancement()} turns {@code sendsTelemetryEvent}
     * <em>on</em> — that flag exists for vanilla's own progression milestones, and
     * every one of Respite's shipped advancements carries it false. The bare
     * constructor is the same builder with the flag left alone, so the generated
     * files keep the posture the hand-authored ones had.
     */
    private static Advancement.Builder builder() {
        return new Advancement.Builder();
    }

    /** A bare "this player did the thing" criterion on one of Respite's own triggers. */
    private static Criterion<PlayerTrigger.TriggerInstance> playerDid(PlayerTrigger trigger) {
        return trigger.createCriterion(new PlayerTrigger.TriggerInstance(Optional.empty()));
    }

    /** Killed a phantom while standing above the cloud layer. */
    private static Criterion<KilledTrigger.TriggerInstance> phantomKilledAboveTheClouds() {
        return CriteriaTriggers.PLAYER_KILLED_ENTITY.createCriterion(new KilledTrigger.TriggerInstance(
                Optional.of(EntityPredicate.wrap(EntityPredicate.Builder.entity()
                        .located(LocationPredicate.Builder.location()
                                .setY(MinMaxBounds.Doubles.atLeast(MOUNTAIN_WATCH_MIN_Y))))),
                Optional.of(EntityPredicate.wrap(EntityPredicate.Builder.entity().of(EntityType.PHANTOM))),
                Optional.empty()));
    }

    private static Component title(String name) {
        return Component.translatable("advancements.respite." + name + ".title");
    }

    private static Component description(String name) {
        return Component.translatable("advancements.respite." + name + ".description");
    }
}
