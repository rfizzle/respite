// Tier: 1 (pure JUnit)
package com.rfizzle.respite.brew;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Caffeinated Brew's shipped resources ({@code design/SPEC.md} §6):
 * both item-name lang keys, both flat item models with a texture on disk, the
 * two recipes' shapes and their {@code respite:feature_enabled} gate, and the
 * two recipe-unlock advancements' matching gate and reward. Catches the drift
 * datagen never sees — a moved texture, a dropped condition, a recipe wired to
 * the wrong item.
 */
class BrewResourceContractTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path LANG = RESOURCES.resolve("assets/respite/lang/en_us.json");

    private static final Gson GSON = new Gson();

    private static JsonObject load(Path path) throws IOException {
        return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
    }

    @Test
    void bothItemNameKeysExistAndAreNonBlank() throws IOException {
        JsonObject lang = load(LANG);
        for (String key : new String[]{"item.respite.unsteeped_brew", "item.respite.caffeinated_brew"}) {
            assertTrue(lang.has(key) && !lang.get(key).getAsString().trim().isEmpty(),
                    key + " must name the item");
        }
    }

    @Test
    void bothItemModelsAreFlatAndPointAtAnItemTextureOnDisk() throws IOException {
        for (String name : new String[]{"unsteeped_brew", "caffeinated_brew"}) {
            Path model = RESOURCES.resolve("assets/respite/models/item/" + name + ".json");
            assertTrue(Files.exists(model), name + " needs an item model");
            JsonObject json = load(model);
            assertEquals("minecraft:item/generated", json.get("parent").getAsString(),
                    name + " item model must parent the flat 2D item model");
            String layer0 = json.getAsJsonObject("textures").get("layer0").getAsString();
            assertEquals("respite:item/" + name, layer0, name + " layer0 must point at its own texture");
            Path texture = RESOURCES.resolve("assets/respite/textures/item/" + name + ".png");
            assertTrue(Files.exists(texture), name + " references missing texture " + layer0);
        }
    }

    @Test
    void unsteepedRecipeIsAGatedShapelessOfWaterCocoaAndLeaves() throws IOException {
        JsonObject recipe = load(RESOURCES.resolve("data/respite/recipe/unsteeped_brew.json"));
        assertGated(recipe, "unsteeped recipe");
        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());

        JsonArray ingredients = recipe.getAsJsonArray("ingredients");
        int cocoa = 0;
        boolean water = false;
        boolean leaves = false;
        for (var element : ingredients) {
            JsonObject ing = element.getAsJsonObject();
            if (ing.has("item") && "minecraft:cocoa_beans".equals(ing.get("item").getAsString())) {
                cocoa++;
            }
            if (ing.has("tag") && "minecraft:leaves".equals(ing.get("tag").getAsString())) {
                leaves = true;
            }
            // The water bottle is constrained to the water potion via a Fabric
            // components ingredient — a plain minecraft:potion would match any potion.
            if (ing.has("components")
                    && "minecraft:water".equals(ing.getAsJsonObject("components")
                            .get("minecraft:potion_contents").getAsString())) {
                water = true;
            }
        }
        assertEquals(2, cocoa, "the shapeless recipe uses exactly two cocoa beans");
        assertTrue(leaves, "the shapeless recipe uses a #minecraft:leaves block");
        assertTrue(water, "the shapeless recipe uses a water bottle constrained to the water potion");

        JsonObject result = recipe.getAsJsonObject("result");
        assertEquals("respite:unsteeped_brew", result.get("id").getAsString());
        assertEquals(1, result.get("count").getAsInt());
    }

    @Test
    void caffeinatedRecipeIsAGatedCampfireCookAt600Ticks() throws IOException {
        JsonObject recipe = load(RESOURCES.resolve("data/respite/recipe/caffeinated_brew.json"));
        assertGated(recipe, "caffeinated recipe");
        assertEquals("minecraft:campfire_cooking", recipe.get("type").getAsString());
        assertEquals("respite:unsteeped_brew", recipe.getAsJsonObject("ingredient").get("item").getAsString());
        assertEquals("respite:caffeinated_brew", recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(600, recipe.get("cookingtime").getAsInt(), "steeping takes 30s (600 ticks)");
    }

    @Test
    void bothUnlockAdvancementsCarryTheGateAndRewardTheirRecipe() throws IOException {
        for (String name : new String[]{"unsteeped_brew", "caffeinated_brew"}) {
            JsonObject advancement =
                    load(RESOURCES.resolve("data/respite/advancement/recipes/misc/" + name + ".json"));
            assertGated(advancement, name + " unlock advancement");
            assertEquals("respite:" + name,
                    advancement.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString(),
                    name + " unlock advancement must reward its own recipe");
        }
    }

    /** Every brew datapack entry gates on {@code respite:feature_enabled} for {@code caffeinated_brew}. */
    private static void assertGated(JsonObject json, String what) {
        assertTrue(json.has("fabric:load_conditions"), what + " must carry fabric:load_conditions");
        JsonObject condition = json.getAsJsonArray("fabric:load_conditions").get(0).getAsJsonObject();
        assertEquals("respite:feature_enabled", condition.get("condition").getAsString(), what);
        assertEquals("caffeinated_brew", condition.get("feature").getAsString(), what);
    }
}
