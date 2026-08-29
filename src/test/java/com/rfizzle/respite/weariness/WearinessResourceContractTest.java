// Tier: 1 (pure JUnit)
package com.rfizzle.respite.weariness;

import com.google.gson.JsonObject;
import com.rfizzle.respite.resources.ShippedResources;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the weariness shipped resources ({@code design/SPEC.md} §4): both effects
 * have a display name in {@code en_us.json} and a {@code mob_effect} icon on disk,
 * so a registered {@code respite:weary}/{@code respite:exhausted} never renders a
 * blank name or a missing-texture checkerboard.
 */
class WearinessResourceContractTest {

    /** Classpath root of the shipped resources — see {@link ShippedResources}. */
    private static final String RESOURCES = "/";
    private static final String LANG = RESOURCES + ("assets/respite/lang/en_us.json");
    private static final String ICONS = RESOURCES + ("assets/respite/textures/mob_effect");

    @Test
    void bothEffectsHaveANonBlankName() throws IOException {
        JsonObject lang = ShippedResources.json(LANG);
        List<String> problems = new ArrayList<>();
        for (String key : new String[] {"effect.respite.weary", "effect.respite.exhausted"}) {
            if (!lang.has(key) || lang.get(key).getAsString().trim().isEmpty()) {
                problems.add("missing or blank " + key);
            }
        }
        assertTrue(problems.isEmpty(), "Weariness lang contract violations: " + problems);
    }

    @Test
    void bothEffectsHaveAnIconOnDisk() {
        List<String> problems = new ArrayList<>();
        for (String icon : new String[] {"weary.png", "exhausted.png"}) {
            if (!ShippedResources.exists(ICONS + ("/" + icon))) {
                problems.add("missing mob_effect texture " + icon);
            }
        }
        assertTrue(problems.isEmpty(), "Weariness icon contract violations: " + problems);
    }
}
