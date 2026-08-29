package com.rfizzle.respite.data;

import com.rfizzle.respite.condition.FeatureEnabledCondition;
import com.rfizzle.respite.registry.RespiteRegistry;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.recipe.v1.ingredient.DefaultCustomIngredients;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.concurrent.CompletableFuture;

/**
 * Respite's crafting recipes ({@code design/SPEC.md} §5, §6, §7).
 *
 * <p>Every recipe is gated on the feature toggle that owns it, through the
 * {@code respite:feature_enabled} resource condition. {@link #withConditions}
 * stamps the condition onto <em>both</em> the recipe and the unlock advancement
 * Minecraft derives from it, which is what keeps a disabled feature from leaving
 * an orphan recipe-book entry behind.
 */
public class RespiteRecipeProvider extends FabricRecipeProvider {

    public RespiteRecipeProvider(FabricDataOutput output,
                                 CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(output, registryLookup);
    }

    @Override
    public void buildRecipes(RecipeOutput exporter) {
        RespiteRegistry.register();

        RecipeOutput chronometer = gatedOn(exporter, FeatureEnabledCondition.Feature.CHRONOMETER);
        RecipeOutput brew = gatedOn(exporter, FeatureEnabledCondition.Feature.CAFFEINATED_BREW);
        RecipeOutput bedroll = gatedOn(exporter, FeatureEnabledCondition.Feature.BEDROLL);

        // The Chronometer — a copper case on a smooth-stone plinth, redstone either
        // side of a clock. REDSTONE is both the book category and the unlock
        // advancement's folder (data/respite/advancement/recipes/redstone/).
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, RespiteRegistry.CHRONOMETER)
                .pattern("CCC")
                .pattern("RKR")
                .pattern("SSS")
                .define('C', Items.COPPER_INGOT)
                .define('R', Items.REDSTONE)
                .define('K', Items.CLOCK)
                .define('S', Items.SMOOTH_STONE)
                .unlockedBy("has_clock", has(Items.CLOCK))
                .save(chronometer);

        // The Pocket Chronometer — the same clock, cased in copper all round.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RespiteRegistry.POCKET_CHRONOMETER)
                .pattern("CCC")
                .pattern("CKC")
                .pattern("CCC")
                .define('C', Items.COPPER_INGOT)
                .define('K', Items.CLOCK)
                .unlockedBy("has_clock", has(Items.CLOCK))
                .save(chronometer);

        // The Unsteeped Brew — a water bottle, cocoa, and any leaf. The bottle is
        // matched on its potion_contents component, so it is a Fabric custom
        // ingredient rather than a plain item: a bare minecraft:potion would also
        // accept every brewed potion in the game.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, RespiteRegistry.UNSTEEPED_BREW)
                .requires(waterBottle())
                .requires(Items.COCOA_BEANS)
                .requires(Items.COCOA_BEANS)
                .requires(ItemTags.LEAVES)
                .unlockedBy("has_cocoa_beans", has(Items.COCOA_BEANS))
                .save(brew);

        // The Caffeinated Brew — thirty seconds over a campfire, no experience.
        // MISC is the RecipeCategory only because it places the unlock advancement
        // in recipes/misc/; the campfire serializer picks the "food" book category
        // itself and ignores what is passed here.
        SimpleCookingRecipeBuilder.campfireCooking(
                        Ingredient.of(RespiteRegistry.UNSTEEPED_BREW),
                        RecipeCategory.MISC,
                        RespiteRegistry.CAFFEINATED_BREW,
                        0.0f,
                        600)
                .unlockedBy("has_unsteeped_brew", has(RespiteRegistry.UNSTEEPED_BREW))
                .save(brew);

        // The Bedroll — string over wool, a bed's comfort without a bed's spawn set.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RespiteRegistry.BEDROLL)
                .pattern("SSS")
                .pattern("WWW")
                .define('S', Items.STRING)
                .define('W', ItemTags.WOOL)
                .unlockedBy("has_wool", has(ItemTags.WOOL))
                .save(bedroll);
    }

    /** A water bottle and nothing else — {@code minecraft:potion} with water contents. */
    private static Ingredient waterBottle() {
        return DefaultCustomIngredients.components(
                Ingredient.of(Items.POTION),
                DataComponentPatch.builder()
                        .set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER))
                        .build());
    }

    /** An exporter that stamps {@code respite:feature_enabled} on recipe and unlock alike. */
    private RecipeOutput gatedOn(RecipeOutput exporter, FeatureEnabledCondition.Feature feature) {
        return withConditions(exporter, new FeatureEnabledCondition(feature));
    }

    @Override
    public String getName() {
        return "Respite Recipes";
    }
}
