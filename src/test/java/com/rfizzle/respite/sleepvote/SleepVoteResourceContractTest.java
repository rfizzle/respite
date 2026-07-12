// Tier: 1 (pure JUnit)
package com.rfizzle.respite.sleepvote;

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
 * Guards the sleep-whisper's shipped chat lines ({@code design/SPEC.md} §1,
 * Sleep whisper + §Localization): both {@code message.respite.*} keys exist and
 * carry exactly the three {@code %s} slots {@link SleepVoteLines} fills (name,
 * sleeping, total). A renamed key or a dropped slot would render a raw
 * translation key or a broken format only in-game; this catches it in
 * milliseconds.
 */
class SleepVoteResourceContractTest {

    private static final Path LANG = Path.of("src/main/resources/assets/respite/lang/en_us.json");

    private static final Gson GSON = new Gson();

    @Test
    void everyChatKeyExistsWithItsArgCount() throws IOException {
        JsonObject lang = GSON.fromJson(Files.readString(LANG, StandardCharsets.UTF_8), JsonObject.class);
        List<String> problems = new ArrayList<>();
        // key → %s count SleepVoteLines passes to Component.translatable
        String[][] expectations = {
                {SleepVoteLines.ENTER_KEY, "3"},
                {SleepVoteLines.LEAVE_KEY, "3"},
        };
        for (String[] expectation : expectations) {
            String key = expectation[0];
            if (!lang.has(key)) {
                problems.add("missing " + key);
                continue;
            }
            String value = lang.get(key).getAsString();
            if (value.startsWith("✦")) {
                problems.add(key + " must not carry the ✦ marker (chat surface, not notification)");
            }
            int args = value.split("%s", -1).length - 1;
            if (args != Integer.parseInt(expectation[1])) {
                problems.add(key + " has " + args + " %s slots, code passes " + expectation[1]);
            }
        }
        assertTrue(problems.isEmpty(), "Sleep-whisper lang contract violations: " + problems);
    }
}
