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
 * Guards the Chronometer's shipped resources: the lang keys the code assembles
 * at runtime (including the {@code _night}/{@code _new_moon} suffixes and every
 * moon phase), their format-arg counts, and the blockstate's power→dial-model
 * mapping with every referenced model present on disk.
 */
class ChronometerResourceContractTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path LANG = RESOURCES.resolve("assets/respite/lang/en_us.json");
    private static final Path BLOCKSTATE = RESOURCES.resolve("assets/respite/blockstates/chronometer.json");

    private static final Gson GSON = new Gson();

    private static JsonObject load(Path path) throws IOException {
        return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
    }

    @Test
    void everyAssembledLangKeyExistsWithItsArgCount() throws IOException {
        JsonObject lang = load(LANG);
        List<String> problems = new ArrayList<>();
        // key → %s count the code passes to Component.translatable
        String[][] expectations = {
                {"time.respite.clock", "2"},
                {"time.respite.am", "0"},
                {"time.respite.pm", "0"},
                {"notification.respite.chronometer", "2"},
                {"notification.respite.chronometer_night", "4"},
                {"notification.respite.chronometer_new_moon", "2"},
                {"notification.respite.chronometer_alarm", "1"},
                {"notification.respite.chronometer_alarm_set", "1"},
                {"notification.respite.chronometer_alarm_off", "0"},
                {"tooltip.respite.chronometer", "2"},
                {"tooltip.respite.chronometer_night", "4"},
                {"tooltip.respite.chronometer_new_moon", "2"},
                {"tooltip.respite.chronometer_alarm", "1"},
        };
        for (String[] expectation : expectations) {
            String key = expectation[0];
            if (!lang.has(key)) {
                problems.add("missing " + key);
                continue;
            }
            String value = lang.get(key).getAsString();
            int args = value.split("%s", -1).length - 1;
            if (args != Integer.parseInt(expectation[1])) {
                problems.add(key + " has " + args + " %s slots, code passes " + expectation[1]);
            }
        }
        assertTrue(problems.isEmpty(), "Chronometer lang contract violations: " + problems);
    }

    @Test
    void everyMoonPhaseKeyExistsAndIsNonBlank() throws IOException {
        JsonObject lang = load(LANG);
        List<String> missing = new ArrayList<>();
        for (int phase = 0; phase < 8; phase++) {
            String key = ChronometerTime.moonPhaseKey(phase);
            if (!lang.has(key) || lang.get(key).getAsString().trim().isEmpty()) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "Missing or blank moon phase lang keys: " + missing);
    }

    @Test
    void blockNameKeyExists() throws IOException {
        JsonObject lang = load(LANG);
        assertTrue(lang.has("block.respite.chronometer")
                        && !lang.get("block.respite.chronometer").getAsString().trim().isEmpty(),
                "block.respite.chronometer must name the block");
    }

    @Test
    void blockstateMapsEveryPowerLevelToItsDialFace() throws IOException {
        JsonObject variants = load(BLOCKSTATE).getAsJsonObject("variants");
        assertEquals("respite:block/chronometer_still",
                variants.getAsJsonObject("power=0").get("model").getAsString(),
                "power=0 is the fixed-time still face");
        for (int power = 1; power <= 15; power++) {
            String variant = "power=" + power;
            assertTrue(variants.has(variant), "blockstate must cover " + variant);
            String expected = "respite:block/chronometer_dial_" + (power - 1) / 2;
            assertEquals(expected, variants.getAsJsonObject(variant).get("model").getAsString(),
                    variant + " must show two-levels-per-face dial " + (power - 1) / 2);
        }
        assertEquals(16, variants.size(), "exactly the 16 power variants, nothing else");
    }

    @Test
    void everyReferencedModelExistsAndPointsAtChronometerTextures() throws IOException {
        JsonObject variants = load(BLOCKSTATE).getAsJsonObject("variants");
        List<String> problems = new ArrayList<>();
        for (String variant : variants.keySet()) {
            String model = variants.getAsJsonObject(variant).get("model").getAsString();
            Path modelPath = RESOURCES.resolve(
                    "assets/respite/models/block/" + model.substring("respite:block/".length()) + ".json");
            if (!Files.exists(modelPath)) {
                problems.add(variant + " references missing model " + model);
                continue;
            }
            JsonObject textures = load(modelPath).getAsJsonObject("textures");
            for (String slot : textures.keySet()) {
                String texture = textures.get(slot).getAsString();
                if (!texture.startsWith("respite:block/chronometer_")) {
                    problems.add(model + " texture slot '" + slot + "' points outside the chronometer set: " + texture);
                    continue;
                }
                Path texturePath = RESOURCES.resolve(
                        "assets/respite/textures/block/" + texture.substring("respite:block/".length()) + ".png");
                if (!Files.exists(texturePath)) {
                    problems.add(model + " references missing texture " + texture);
                }
            }
        }
        assertTrue(problems.isEmpty(), "Chronometer model contract violations: " + problems);
    }

    @Test
    void recipeMatchesTheSpecGridAndCarriesTheFeatureGate() throws IOException {
        JsonObject recipe = load(RESOURCES.resolve("data/respite/recipe/chronometer.json"));
        assertConditionGated(recipe, "recipe");
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        var pattern = recipe.getAsJsonArray("pattern");
        assertEquals("CCC", pattern.get(0).getAsString());
        assertEquals("RKR", pattern.get(1).getAsString());
        assertEquals("SSS", pattern.get(2).getAsString());
        JsonObject key = recipe.getAsJsonObject("key");
        assertEquals("minecraft:copper_ingot", key.getAsJsonObject("C").get("item").getAsString());
        assertEquals("minecraft:redstone", key.getAsJsonObject("R").get("item").getAsString());
        assertEquals("minecraft:clock", key.getAsJsonObject("K").get("item").getAsString());
        assertEquals("minecraft:smooth_stone", key.getAsJsonObject("S").get("item").getAsString());
        JsonObject result = recipe.getAsJsonObject("result");
        assertEquals("respite:chronometer", result.get("id").getAsString());
        assertEquals(1, result.get("count").getAsInt());
    }

    @Test
    void recipeUnlockAdvancementCarriesTheSameFeatureGate() throws IOException {
        JsonObject advancement =
                load(RESOURCES.resolve("data/respite/advancement/recipes/redstone/chronometer.json"));
        assertConditionGated(advancement, "unlock advancement");
        assertEquals("respite:chronometer",
                advancement.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString(),
                "the unlock advancement must reward exactly the gated recipe");
    }

    /** Both datapack entries must gate on {@code respite:feature_enabled} for the chronometer. */
    private static void assertConditionGated(JsonObject json, String what) {
        assertTrue(json.has("fabric:load_conditions"), what + " must carry fabric:load_conditions");
        JsonObject condition = json.getAsJsonArray("fabric:load_conditions").get(0).getAsJsonObject();
        assertEquals("respite:feature_enabled", condition.get("condition").getAsString(), what);
        assertEquals("chronometer", condition.get("feature").getAsString(), what);
    }

    @Test
    void itemModelParentsABlockModel() throws IOException {
        Path itemModel = RESOURCES.resolve("assets/respite/models/item/chronometer.json");
        assertTrue(Files.exists(itemModel), "the BlockItem needs an item model");
        String parent = load(itemModel).get("parent").getAsString();
        assertTrue(parent.startsWith("respite:block/chronometer_"),
                "item model should parent a chronometer block model, got " + parent);
    }
}
