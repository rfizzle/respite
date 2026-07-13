// Tier: 1 (pure JUnit)
package com.rfizzle.respite.chronometer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the pocket chronometer's shipped resources ({@code design/SPEC.md} §5):
 * the tooltip and name lang keys with the format-arg counts the code passes, the
 * flat item model and its texture, and the recipe plus its unlock advancement —
 * both gated on the shared {@code chronometer} feature toggle.
 */
class PocketChronometerResourceContractTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path LANG = RESOURCES.resolve("assets/respite/lang/en_us.json");

    private static final Gson GSON = new Gson();

    private static JsonObject load(Path path) throws IOException {
        return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
    }

    @Test
    void everyAssembledTooltipKeyExistsWithItsArgCount() throws IOException {
        JsonObject lang = load(LANG);
        List<String> problems = new ArrayList<>();
        String[][] expectations = {
                {"tooltip.respite.pocket_chronometer", "1"},
                {"tooltip.respite.pocket_chronometer_night", "3"},
                {"tooltip.respite.pocket_chronometer_new_moon", "1"},
                {"tooltip.respite.pocket_chronometer_still", "0"},
                {"tooltip.respite.pocket_chronometer_awake", "1"},
        };
        for (String[] expectation : expectations) {
            String key = expectation[0];
            if (!lang.has(key)) {
                problems.add("missing " + key);
                continue;
            }
            int args = lang.get(key).getAsString().split("%s", -1).length - 1;
            if (args != Integer.parseInt(expectation[1])) {
                problems.add(key + " has " + args + " %s slots, code passes " + expectation[1]);
            }
        }
        assertTrue(problems.isEmpty(), "Pocket chronometer lang contract violations: " + problems);
    }

    @Test
    void itemNameKeyExists() throws IOException {
        JsonObject lang = load(LANG);
        assertTrue(lang.has("item.respite.pocket_chronometer")
                        && !lang.get("item.respite.pocket_chronometer").getAsString().trim().isEmpty(),
                "item.respite.pocket_chronometer must name the item");
    }

    @Test
    void itemModelIsAFlatIconWithItsTexturePresent() throws IOException {
        Path model = RESOURCES.resolve("assets/respite/models/item/pocket_chronometer.json");
        assertTrue(Files.exists(model), "the pocket chronometer needs an item model");
        JsonObject json = load(model);
        assertEquals("minecraft:item/generated", json.get("parent").getAsString(),
                "a carried timepiece is a flat generated icon, not a block model");
        String layer0 = json.getAsJsonObject("textures").get("layer0").getAsString();
        assertEquals("respite:item/pocket_chronometer", layer0);
        Path texture = RESOURCES.resolve(
                "assets/respite/textures/item/" + layer0.substring("respite:item/".length()) + ".png");
        assertTrue(Files.exists(texture), "layer0 texture must ship: " + texture);
    }

    @Test
    void recipeIsTheCopperFramedClockAndCarriesTheFeatureGate() throws IOException {
        JsonObject recipe = load(RESOURCES.resolve("data/respite/recipe/pocket_chronometer.json"));
        assertConditionGated(recipe, "recipe");
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        var pattern = recipe.getAsJsonArray("pattern");
        assertEquals("CCC", pattern.get(0).getAsString());
        assertEquals("CKC", pattern.get(1).getAsString());
        assertEquals("CCC", pattern.get(2).getAsString());
        JsonObject key = recipe.getAsJsonObject("key");
        assertEquals("minecraft:copper_ingot", key.getAsJsonObject("C").get("item").getAsString());
        assertEquals("minecraft:clock", key.getAsJsonObject("K").get("item").getAsString());
        JsonObject result = recipe.getAsJsonObject("result");
        assertEquals("respite:pocket_chronometer", result.get("id").getAsString());
        assertEquals(1, result.get("count").getAsInt());
    }

    @Test
    void recipeUnlockAdvancementCarriesTheSameFeatureGate() throws IOException {
        JsonObject advancement =
                load(RESOURCES.resolve("data/respite/advancement/recipes/misc/pocket_chronometer.json"));
        assertConditionGated(advancement, "unlock advancement");
        assertEquals("respite:pocket_chronometer",
                advancement.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString(),
                "the unlock advancement must reward exactly the gated recipe");
    }

    /** Both datapack entries share the block's {@code chronometer} feature gate — one toggle, two forms. */
    private static void assertConditionGated(JsonObject json, String what) {
        assertTrue(json.has("fabric:load_conditions"), what + " must carry fabric:load_conditions");
        JsonObject condition = json.getAsJsonArray("fabric:load_conditions").get(0).getAsJsonObject();
        assertEquals("respite:feature_enabled", condition.get("condition").getAsString(), what);
        assertEquals("chronometer", condition.get("feature").getAsString(), what);
    }
}
