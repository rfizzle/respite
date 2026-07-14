// Tier: 1 (pure JUnit)
package com.rfizzle.respite.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rfizzle.respite.command.ConfigDiff;
import org.junit.jupiter.api.Test;

/**
 * The config-sync payload's serialize → wire-string → deserialize round-trip
 * ({@code design/SPEC.md} §Configuration). Guards that a synced config reproduces
 * the server's values field-for-field, that a hostile or corrupt frame is clamped
 * rather than trusted, and that unparseable JSON degrades to defaults.
 */
class RespiteConfigPayloadTest {

    @Test
    void roundTripReproducesEveryField() {
        RespiteConfig source = new RespiteConfig();
        // Move a spread of fields off their defaults so the round-trip has to carry
        // real values, not just re-derive the default config.
        source.maxTimeLapseRate = 42;
        source.timeLapseTickBudgetMs = 25;
        source.combatHoldsTime = false;
        source.enablePhantomRework = false;
        source.phantomAltitudeMin = 64;
        source.wearinessThresholdDays = 5;
        source.wearinessRegenPenalty = 0.4;
        source.exhaustedThresholdDays = 9;
        source.newMoonHealMultiplier = 3.5;
        source.brewHasteSeconds = 30;
        source.bedrollRestfulMultiplier = 0.25;
        source.showTimeLapseMessages = false;

        RespiteConfig restored = RespiteConfigPayload.of(source).toConfig();

        assertTrue(ConfigDiff.changedFields(source, restored).isEmpty(),
                "synced config must match the source field-for-field; differed: "
                        + ConfigDiff.changedFields(source, restored));
    }

    @Test
    void toConfigClampsOutOfRangeValues() {
        // A frame carrying a value past the field's range must not seat it verbatim.
        String json = "{\"maxTimeLapseRate\": 999, \"phantomAltitudeMin\": 5000,"
                + " \"wearinessRegenPenalty\": 9.0}";
        RespiteConfig config = new RespiteConfigPayload(json).toConfig();

        assertEquals(RespiteConfig.Bounds.MAX_TIME_LAPSE_RATE.max(), config.maxTimeLapseRate);
        assertEquals(RespiteConfig.Bounds.PHANTOM_ALTITUDE_MIN.max(), config.phantomAltitudeMin);
        assertEquals(RespiteConfig.Bounds.WEARINESS_REGEN_PENALTY.max(), config.wearinessRegenPenalty);
    }

    @Test
    void malformedJsonFallsBackToDefaults() {
        RespiteConfig defaults = new RespiteConfig();
        RespiteConfig config = new RespiteConfigPayload("not json at all {{{").toConfig();

        assertTrue(ConfigDiff.changedFields(defaults, config).isEmpty(),
                "an unparseable frame must degrade to a clean default config");
    }
}
