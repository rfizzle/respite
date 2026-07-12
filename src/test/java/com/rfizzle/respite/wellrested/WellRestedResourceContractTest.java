// Tier: 1 (pure JUnit)
package com.rfizzle.respite.wellrested;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Well-Rested shipped resources ({@code design/SPEC.md} §4): the effect
 * has a display name in {@code en_us.json} and a {@code mob_effect} icon on disk,
 * so a registered {@code respite:well_rested} never renders a blank name or a
 * missing-texture checkerboard.
 */
class WellRestedResourceContractTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path LANG = RESOURCES.resolve("assets/respite/lang/en_us.json");
    private static final Path ICON =
            RESOURCES.resolve("assets/respite/textures/mob_effect/well_rested.png");

    @Test
    void theEffectHasANonBlankName() throws IOException {
        JsonObject lang = new Gson().fromJson(
                Files.readString(LANG, StandardCharsets.UTF_8), JsonObject.class);
        assertTrue(lang.has("effect.respite.well_rested")
                        && !lang.get("effect.respite.well_rested").getAsString().trim().isEmpty(),
                "effect.respite.well_rested must have a non-blank display name");
    }

    @Test
    void theEffectHasAnIconOnDisk() {
        assertTrue(Files.exists(ICON), "missing mob_effect texture well_rested.png");
    }

    @Test
    void theRenderPreviewIsNotShipped() {
        assertFalse(Files.exists(RESOURCES.resolve(
                        "assets/respite/textures/mob_effect/well_rested@16x.png")),
                "the @16x glyph preview must not ship in the jar");
    }
}
