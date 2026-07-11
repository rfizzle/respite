// Tier: 1 (pure JUnit)
package com.rfizzle.respite.restful;

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
 * Guards the restful-saturation shipped resources ({@code design/SPEC.md}
 * §Localization): the two wake action-bar keys exist, carry the suite's ✦
 * notification marker inside the localized value, and take no format args —
 * exactly what {@code RestfulMath.wakeLineKey} hands to
 * {@code Component.translatable}.
 */
class RestfulResourceContractTest {

    private static final Path LANG = Path.of("src/main/resources/assets/respite/lang/en_us.json");

    @Test
    void bothWakeLinesExistAsMarkedZeroArgNotifications() throws IOException {
        JsonObject lang = new Gson().fromJson(
                Files.readString(LANG, StandardCharsets.UTF_8), JsonObject.class);
        List<String> problems = new ArrayList<>();
        for (String key : new String[] {
                "notification.respite.rested",
                "notification.respite.deep_rested",
        }) {
            if (!lang.has(key)) {
                problems.add("missing " + key);
                continue;
            }
            String value = lang.get(key).getAsString();
            if (!value.startsWith("✦ ")) {
                problems.add(key + " must carry the ✦ marker inside the localized value, got: " + value);
            }
            int args = value.split("%s", -1).length - 1;
            if (args != 0) {
                problems.add(key + " has " + args + " %s slots, code passes 0");
            }
        }
        assertTrue(problems.isEmpty(), "Restful lang contract violations: " + problems);
    }
}
