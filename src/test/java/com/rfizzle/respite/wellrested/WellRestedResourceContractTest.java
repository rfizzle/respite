// Tier: 1 (pure JUnit)
package com.rfizzle.respite.wellrested;

import com.google.gson.JsonObject;
import com.rfizzle.respite.resources.ShippedResources;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Well-Rested shipped resources ({@code design/SPEC.md} §4): the effect
 * has a display name in {@code en_us.json} and a {@code mob_effect} icon on disk,
 * so a registered {@code respite:well_rested} never renders a blank name or a
 * missing-texture checkerboard.
 */
class WellRestedResourceContractTest {

    /** Classpath root of the shipped resources — see {@link ShippedResources}. */
    private static final String RESOURCES = "/";
    private static final String LANG = RESOURCES + ("assets/respite/lang/en_us.json");
    private static final String ICON =
            RESOURCES + ("assets/respite/textures/mob_effect/well_rested.png");

    @Test
    void theEffectHasANonBlankName() throws IOException {
        JsonObject lang = ShippedResources.json(LANG);
        assertTrue(lang.has("effect.respite.well_rested")
                        && !lang.get("effect.respite.well_rested").getAsString().trim().isEmpty(),
                "effect.respite.well_rested must have a non-blank display name");
    }

    @Test
    void theEffectHasAnIconOnDisk() {
        assertTrue(ShippedResources.exists(ICON), "missing mob_effect texture well_rested.png");
    }

    @Test
    void theRenderPreviewIsNotShipped() {
        assertFalse(ShippedResources.exists(RESOURCES + (
                        "assets/respite/textures/mob_effect/well_rested@16x.png")),
                "the @16x glyph preview must not ship in the jar");
    }
}
