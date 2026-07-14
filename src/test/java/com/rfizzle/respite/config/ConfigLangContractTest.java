// Tier: 1 (pure JUnit)
package com.rfizzle.respite.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the config lang contract from both directions: every config field has a
 * {@code config.respite.<key>} label plus a non-blank {@code .tooltip}, and every
 * label in {@code en_us.json} points back at a real field — so the Cloth screen
 * never renders a raw key and a renamed field can't orphan its lang entries.
 */
class ConfigLangContractTest {

    private static final Path LANG =
            Path.of("src/main/resources/assets/respite/lang/en_us.json");

    private static JsonObject loadLang() throws IOException {
        String json = Files.readString(LANG, StandardCharsets.UTF_8);
        return new Gson().fromJson(json, JsonObject.class);
    }

    /** Config fields the screen exposes — every public instance field except the schema version. */
    private static List<String> configKeys() {
        List<String> keys = new ArrayList<>();
        for (Field field : RespiteConfig.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (field.getName().equals("configVersion")) continue;
            keys.add(field.getName());
        }
        return keys;
    }

    @Test
    void everyConfigFieldHasALabelAndTooltip() throws IOException {
        JsonObject lang = loadLang();
        List<String> missing = new ArrayList<>();
        for (String key : configKeys()) {
            String label = "config.respite." + key;
            if (!lang.has(label)) missing.add(label);
            if (!lang.has(label + ".tooltip")) missing.add(label + ".tooltip");
        }
        assertTrue(missing.isEmpty(), "Config fields missing lang entries: " + missing);
    }

    @Test
    void everyConfigLabelPointsAtARealField() throws IOException {
        JsonObject lang = loadLang();
        List<String> fields = configKeys();
        List<String> orphaned = new ArrayList<>();
        for (Map.Entry<String, ?> e : lang.entrySet()) {
            String key = e.getKey();
            if (!isConfigLabel(key)) continue;
            if (!fields.contains(key.substring("config.respite.".length()))) {
                orphaned.add(key);
            }
        }
        assertTrue(orphaned.isEmpty(), "Lang labels with no matching config field: " + orphaned);
    }

    @Test
    void noLabelOrTooltipIsBlank() throws IOException {
        JsonObject lang = loadLang();
        List<String> blank = new ArrayList<>();
        for (Map.Entry<String, ?> e : lang.entrySet()) {
            String key = e.getKey();
            if (!key.startsWith("config.respite.")) continue;
            if (lang.get(key).getAsString().trim().isEmpty()) {
                blank.add(key);
            }
        }
        assertTrue(blank.isEmpty(), "Blank config lang entries: " + blank);
    }

    @Test
    void langFileShipsOnTheMainResourcePath() {
        assertTrue(Files.exists(LANG), "en_us.json must live in src/main/resources so it ships in the jar");
        assertFalse(Files.exists(Path.of("src/client/resources/assets/respite/lang/en_us.json")),
                "lang must not fork into the client resource root");
    }

    /** A config entry label: {@code config.respite.<key>}, not the title, a category, a screen banner, or a tooltip. */
    private static boolean isConfigLabel(String key) {
        return key.startsWith("config.respite.")
                && !key.equals("config.respite.title")
                && !key.equals("config.respite.server_controlled_note")
                && !key.contains(".category.")
                && !key.endsWith(".tooltip");
    }
}
