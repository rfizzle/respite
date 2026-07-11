// Tier: 1 (pure JUnit)
package com.rfizzle.respite.condition;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import com.rfizzle.respite.condition.FeatureEnabledCondition.Feature;
import com.rfizzle.respite.config.RespiteConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the {@code respite:feature_enabled} condition: each feature reads
 * exactly its config toggle, the codec accepts the shipped spelling, and an
 * unknown feature name is a parse error (fails loudly at datapack load) rather
 * than a silent default.
 */
class FeatureEnabledConditionTest {

    @Test
    void chronometerFeatureTracksItsToggle() {
        RespiteConfig config = new RespiteConfig();
        config.enableChronometer = true;
        assertTrue(Feature.CHRONOMETER.enabledIn(config));
        config.enableChronometer = false;
        assertFalse(Feature.CHRONOMETER.enabledIn(config));
    }

    @Test
    void codecParsesTheShippedSpelling() {
        var result = Feature.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("chronometer"));
        assertEquals(Feature.CHRONOMETER, result.result().orElseThrow());
    }

    @Test
    void codecRejectsAnUnknownFeature() {
        var result = Feature.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("time_machine"));
        assertTrue(result.error().isPresent(), "unknown feature must fail parse, not default");
    }

    @Test
    void conditionCodecReadsTheFeatureField() {
        JsonObject json = new JsonObject();
        json.addProperty("feature", "chronometer");
        var result = FeatureEnabledCondition.CODEC.codec().parse(JsonOps.INSTANCE, json);
        assertEquals(Feature.CHRONOMETER, result.result().orElseThrow().feature());
    }
}
