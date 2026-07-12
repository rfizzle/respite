// Tier: 1 (pure JUnit)
package com.rfizzle.respite.command;

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
 * Guards {@code /respite}'s shipped feedback strings ({@code design/SPEC.md}
 * §Commands + §Localization): every {@code command.respite.*} key the command
 * class passes to {@code Component.translatable} exists in {@code en_us.json}
 * with the {@code %s} slot count the call site supplies.
 */
class CommandResourceContractTest {

    private static final Path LANG = Path.of("src/main/resources/assets/respite/lang/en_us.json");
    private static final Gson GSON = new Gson();

    @Test
    void everyCommandKeyExistsWithItsArgCount() throws IOException {
        JsonObject lang = GSON.fromJson(Files.readString(LANG, StandardCharsets.UTF_8), JsonObject.class);
        List<String> problems = new ArrayList<>();
        // key → %s count the command layer passes
        String[][] expectations = {
                {"command.respite.not_player", "0"},
                {"command.respite.status.time_held", "0"},
                {"command.respite.status.time_active", "3"},
                {"command.respite.status.time_settled", "0"},
                {"command.respite.status.awake", "2"},
                {"command.respite.status.stage.rested", "0"},
                {"command.respite.status.stage.weary", "0"},
                {"command.respite.status.stage.exhausted", "0"},
                {"command.respite.status.new_moon", "1"},
                {"command.respite.status.new_moon_tonight", "0"},
                {"command.respite.status.chronometer", "1"},
                {"command.respite.reload", "1"},
                {"command.respite.reload_failed", "1"},
                {"command.respite.reload_recipes_failed", "0"},
                {"command.respite.rest.clear", "1"},
                {"command.respite.rest.set", "2"},
        };
        for (String[] expectation : expectations) {
            String key = expectation[0];
            if (!lang.has(key)) {
                problems.add("missing " + key);
                continue;
            }
            String value = lang.get(key).getAsString();
            if (value.trim().isEmpty()) {
                problems.add(key + " is blank");
                continue;
            }
            int args = value.split("%s", -1).length - 1;
            if (args != Integer.parseInt(expectation[1])) {
                problems.add(key + " has " + args + " %s slots, code passes " + expectation[1]);
            }
        }
        assertTrue(problems.isEmpty(), "Command lang contract violations: " + problems);
    }
}
