// Tier: 1 (pure JUnit)
package com.rfizzle.respite.bedroll;

import com.google.gson.JsonObject;
import com.rfizzle.respite.resources.ShippedResources;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the bedroll's shipped resources ({@code design/SPEC.md} §7): its lang
 * keys, the feature-gated shaped recipe and its unlock advancement, the
 * self-drop loot table, and the blockstate → model → texture chain plus the
 * item model, with every referenced file present on disk.
 */
class BedrollResourceContractTest {

    /** Classpath root of the shipped resources — see {@link ShippedResources}. */
    private static final String RESOURCES = "/";
    private static final String LANG = RESOURCES + ("assets/respite/lang/en_us.json");

    private static JsonObject load(String path) {
        return ShippedResources.json(path);
    }

    @Test
    void everyLangKeyExistsAndIsNonBlank() throws IOException {
        JsonObject lang = load(LANG);
        List<String> missing = new ArrayList<>();
        for (String key : List.of(
                "block.respite.bedroll",
                "config.respite.category.bedroll",
                "config.respite.enableBedroll",
                "config.respite.enableBedroll.tooltip",
                "config.respite.bedrollRestfulMultiplier",
                "config.respite.bedrollRestfulMultiplier.tooltip")) {
            if (!lang.has(key) || lang.get(key).getAsString().trim().isEmpty()) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "Missing or blank bedroll lang keys: " + missing);
    }

    @Test
    void recipeIsStringOverWoolAndFeatureGated() throws IOException {
        JsonObject recipe = load(RESOURCES + ("data/respite/recipe/bedroll.json"));
        assertConditionGated(recipe, "recipe");
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        var pattern = recipe.getAsJsonArray("pattern");
        assertEquals("SSS", pattern.get(0).getAsString());
        assertEquals("WWW", pattern.get(1).getAsString());
        assertEquals(2, pattern.size(), "a 2×3 grid, string over wool");
        JsonObject key = recipe.getAsJsonObject("key");
        assertEquals("minecraft:string", key.getAsJsonObject("S").get("item").getAsString());
        assertEquals("minecraft:wool", key.getAsJsonObject("W").get("tag").getAsString(),
                "any wool colour via the tag");
        JsonObject result = recipe.getAsJsonObject("result");
        assertEquals("respite:bedroll", result.get("id").getAsString());
        assertEquals(1, result.get("count").getAsInt());
    }

    @Test
    void recipeUnlockAdvancementCarriesTheSameFeatureGate() throws IOException {
        JsonObject advancement =
                load(RESOURCES + ("data/respite/advancement/recipes/misc/bedroll.json"));
        assertConditionGated(advancement, "unlock advancement");
        assertEquals("respite:bedroll",
                advancement.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString(),
                "the unlock advancement must reward exactly the gated recipe");
    }

    @Test
    void lootTableDropsItself() throws IOException {
        JsonObject loot = load(RESOURCES + ("data/respite/loot_table/blocks/bedroll.json"));
        String dropped = loot.getAsJsonArray("pools").get(0).getAsJsonObject()
                .getAsJsonArray("entries").get(0).getAsJsonObject()
                .get("name").getAsString();
        assertEquals("respite:bedroll", dropped, "a broken bedroll drops itself");
    }

    @Test
    void blockstateMapsEveryFacingToTheBedrollModelWithTexturesPresent() throws IOException {
        JsonObject variants = load(RESOURCES + ("assets/respite/blockstates/bedroll.json"))
                .getAsJsonObject("variants");
        List<String> problems = new ArrayList<>();
        for (String facing : List.of("north", "east", "south", "west")) {
            String variant = "facing=" + facing;
            if (!variants.has(variant)) {
                problems.add("blockstate must cover " + variant);
                continue;
            }
            String model = variants.getAsJsonObject(variant).get("model").getAsString();
            assertEquals("respite:block/bedroll", model, variant + " uses the bedroll model");
        }
        assertEquals(4, variants.size(), "exactly the four facing variants");

        JsonObject textures = load(RESOURCES + ("assets/respite/models/block/bedroll.json"))
                .getAsJsonObject("textures");
        for (String slot : textures.keySet()) {
            String texture = textures.get(slot).getAsString();
            if (!texture.startsWith("respite:block/bedroll")) {
                problems.add("model texture slot '" + slot + "' points outside the bedroll set: " + texture);
                continue;
            }
            String texturePath = RESOURCES + (
                    "assets/respite/textures/block/" + texture.substring("respite:block/".length()) + ".png");
            if (!ShippedResources.exists(texturePath)) {
                problems.add("model references missing texture " + texture);
            }
        }
        assertTrue(problems.isEmpty(), "Bedroll model contract violations: " + problems);
    }

    @Test
    void itemModelIsAFlatIconWithItsTexturePresent() throws IOException {
        String itemModel = RESOURCES + ("assets/respite/models/item/bedroll.json");
        assertTrue(ShippedResources.exists(itemModel), "the item needs an item model");
        JsonObject model = load(itemModel);
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        String layer0 = model.getAsJsonObject("textures").get("layer0").getAsString();
        assertEquals("respite:item/bedroll", layer0);
        String texture = RESOURCES + ("assets/respite/textures/item/bedroll.png");
        assertTrue(ShippedResources.exists(texture), "the item texture must ship: " + texture);
    }

    /** Both datapack entries must gate on {@code respite:feature_enabled} for the bedroll. */
    private static void assertConditionGated(JsonObject json, String what) {
        assertTrue(json.has("fabric:load_conditions"), what + " must carry fabric:load_conditions");
        JsonObject condition = json.getAsJsonArray("fabric:load_conditions").get(0).getAsJsonObject();
        assertEquals("respite:feature_enabled", condition.get("condition").getAsString(), what);
        assertEquals("bedroll", condition.get("feature").getAsString(), what);
    }
}
