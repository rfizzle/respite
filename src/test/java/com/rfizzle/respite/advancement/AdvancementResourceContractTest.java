// Tier: 1 (pure JUnit)
package com.rfizzle.respite.advancement;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the shipped advancement tab ({@code design/SPEC.md} §Advancements +
 * §Localization): every advancement JSON parses, wires its title/description to
 * the {@code advancements.respite.<id>.*} keys, and each of those keys exists
 * non-blank in {@code en_us.json}. The actual grant is a gametest; this catches
 * a renamed key or a missing file before the server ever loads the datapack.
 */
class AdvancementResourceContractTest {

    private static final Path ADVANCEMENTS = Path.of("src/main/resources/data/respite/advancement");
    private static final Path LANG = Path.of("src/main/resources/assets/respite/lang/en_us.json");
    private static final Gson GSON = new Gson();

    private static final String[] IDS = {
            "root", "beauty_sleep", "night_shift", "mountain_watch", "clockwork", "dark_and_dreamless",
    };

    @Test
    void everyAdvancementParsesAndWiresItsLangKeys() throws IOException {
        JsonObject lang = GSON.fromJson(Files.readString(LANG, StandardCharsets.UTF_8), JsonObject.class);
        List<String> problems = new ArrayList<>();
        for (String id : IDS) {
            Path file = ADVANCEMENTS.resolve(id + ".json");
            if (!Files.exists(file)) {
                problems.add("missing advancement file " + id + ".json");
                continue;
            }
            JsonObject advancement = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
            if (advancement == null || !advancement.has("display")) {
                problems.add(id + " has no display block");
                continue;
            }
            JsonObject display = advancement.getAsJsonObject("display");
            checkTranslateKey(lang, problems, id, display, "title", "advancements.respite." + id + ".title");
            checkTranslateKey(lang, problems, id, display, "description", "advancements.respite." + id + ".description");
            if (!advancement.has("criteria") || advancement.getAsJsonObject("criteria").size() == 0) {
                problems.add(id + " has no criteria");
            }
        }
        assertTrue(problems.isEmpty(), "Advancement contract violations: " + problems);
    }

    private static void checkTranslateKey(JsonObject lang, List<String> problems, String id,
            JsonObject display, String field, String expectedKey) {
        if (!display.has(field) || !display.getAsJsonObject(field).has("translate")) {
            problems.add(id + " display." + field + " is not a translate component");
            return;
        }
        String key = display.getAsJsonObject(field).get("translate").getAsString();
        if (!expectedKey.equals(key)) {
            problems.add(id + " display." + field + " points at " + key + ", expected " + expectedKey);
            return;
        }
        if (!lang.has(key) || lang.get(key).getAsString().trim().isEmpty()) {
            problems.add(key + " missing or blank in en_us.json");
        }
    }
}
