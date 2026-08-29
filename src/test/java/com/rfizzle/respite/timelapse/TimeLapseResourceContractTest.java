// Tier: 1 (pure JUnit)
package com.rfizzle.respite.timelapse;

import com.google.gson.JsonObject;
import com.rfizzle.respite.resources.ShippedResources;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the time-lapse's shipped resources ({@code design/SPEC.md}
 * §Localization + §Sound Design): the action-bar keys with their format-arg
 * counts, and the full sound chain — every {@code sounds.json} event carries
 * a subtitle whose lang key exists, and every referenced {@code .ogg} is
 * actually on disk.
 */
class TimeLapseResourceContractTest {

    /** Classpath root of the shipped resources — see {@link ShippedResources}. */
    private static final String RESOURCES = "/";
    private static final String LANG = RESOURCES + ("assets/respite/lang/en_us.json");
    private static final String SOUNDS = RESOURCES + ("assets/respite/sounds.json");

    private static JsonObject load(String path) {
        return ShippedResources.json(path);
    }

    @Test
    void everyActionBarKeyExistsWithItsArgCount() throws IOException {
        JsonObject lang = load(LANG);
        List<String> problems = new ArrayList<>();
        // key → %s count TimeLapseLines passes to Component.translatable
        String[][] expectations = {
                {"notification.respite.time_lapse", "3"},
                {"notification.respite.time_lapse_end", "0"},
                {"notification.respite.time_hold", "0"},
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
        assertTrue(problems.isEmpty(), "Time-lapse lang contract violations: " + problems);
    }

    @Test
    void everySoundEventIsFullyWired() throws IOException {
        JsonObject sounds = load(SOUNDS);
        JsonObject lang = load(LANG);
        List<String> problems = new ArrayList<>();
        for (String event : new String[] {"ui.time_lapse.start", "ui.time_lapse.end"}) {
            if (!sounds.has(event)) {
                problems.add("sounds.json missing event " + event);
                continue;
            }
            JsonObject entry = sounds.getAsJsonObject(event);
            if (!entry.has("subtitle")) {
                problems.add(event + " has no subtitle (accessibility is non-negotiable)");
            } else {
                String subtitle = entry.get("subtitle").getAsString();
                if (!lang.has(subtitle) || lang.get(subtitle).getAsString().trim().isEmpty()) {
                    problems.add("subtitle key " + subtitle + " missing or blank in en_us.json");
                }
            }
            for (var sound : entry.getAsJsonArray("sounds")) {
                String name = sound.getAsString();
                String ogg = RESOURCES + ("assets/respite/sounds/"
                        + name.substring("respite:".length()) + ".ogg");
                if (!ShippedResources.exists(ogg)) {
                    problems.add(event + " references " + name + " but " + ogg + " does not exist");
                }
            }
        }
        assertTrue(problems.isEmpty(), "Time-lapse sound contract violations: " + problems);
    }

    @Test
    void committedSfxSourcesExistForEveryShippedCue() {
        // the repeatability rule: the .sfx spec is the committed source of truth
        List<String> missing = new ArrayList<>();
        for (String source : new String[] {"time-lapse-start.sfx", "time-lapse-end.sfx"}) {
            if (!Files.exists(Path.of("art/audio").resolve(source))) {
                missing.add(source);
            }
        }
        assertTrue(missing.isEmpty(), "Missing committed .sfx sources: " + missing);
    }
}
