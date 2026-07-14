// Tier: 1 (pure JUnit)
package com.rfizzle.respite.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RespiteConfigTest {
    private static final Gson GSON = RespiteConfig.GSON;

    // Every key the spec's Configuration table names, server then client.
    private static final List<String> SPEC_KEYS = List.of(
            "enableTimeLapse", "maxTimeLapseRate", "timeLapseTickBudgetMs", "combatHoldsTime",
            "announceTimeLapse", "announceSleepVote", "excludeIdleFromShare", "idleThresholdMinutes",
            "enableRestfulSaturation", "restfulRequiresFullHunger",
            "restfulHealIntervalTicks", "newMoonHealMultiplier", "enablePhantomRework",
            "phantomAltitudeMin", "phantomNewMoon", "enableWeariness", "wearinessThresholdDays",
            "wearinessRegenPenalty", "exhaustedThresholdDays", "exhaustedRegenPenalty",
            "enableWellRested", "wellRestedSeconds", "wellRestedRegenBonus",
            "enableChronometer", "enableCaffeinatedBrew", "brewHasteSeconds",
            "enableBedroll", "bedrollRestfulMultiplier",
            "showTimeLapseMessages", "showExhaustionBlink");

    @Test
    void defaultValuesMatchTheSpecTable() {
        RespiteConfig config = new RespiteConfig();

        assertEquals(1, config.configVersion);
        assertTrue(config.enableTimeLapse);
        assertEquals(20, config.maxTimeLapseRate);
        assertEquals(40, config.timeLapseTickBudgetMs);
        assertTrue(config.combatHoldsTime);
        assertTrue(config.announceTimeLapse);
        assertTrue(config.announceSleepVote);
        assertTrue(config.excludeIdleFromShare);
        assertEquals(5, config.idleThresholdMinutes);
        assertTrue(config.enableRestfulSaturation);
        assertTrue(config.restfulRequiresFullHunger);
        assertEquals(600, config.restfulHealIntervalTicks);
        assertEquals(2.0, config.newMoonHealMultiplier);
        assertTrue(config.enablePhantomRework);
        assertEquals(100, config.phantomAltitudeMin);
        assertTrue(config.phantomNewMoon);
        assertTrue(config.enableWeariness);
        assertEquals(3, config.wearinessThresholdDays);
        assertEquals(0.25, config.wearinessRegenPenalty);
        assertEquals(6, config.exhaustedThresholdDays);
        assertEquals(0.50, config.exhaustedRegenPenalty);
        assertTrue(config.enableWellRested);
        assertEquals(120, config.wellRestedSeconds);
        assertEquals(0.5, config.wellRestedRegenBonus);
        assertTrue(config.enableChronometer);
        assertTrue(config.enableCaffeinatedBrew);
        assertEquals(90, config.brewHasteSeconds);
        assertTrue(config.enableBedroll);
        assertEquals(0.5, config.bedrollRestfulMultiplier);
        assertTrue(config.showTimeLapseMessages);
        assertTrue(config.showExhaustionBlink);
    }

    @Test
    void firstLaunchWritesEveryKeyWithDefaults(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("respite.json");

        RespiteConfig config = RespiteConfig.load(path);

        assertTrue(Files.exists(path), "first launch must write the config file");
        JsonObject written = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals(1, written.get("configVersion").getAsInt());
        for (String key : SPEC_KEYS) {
            assertTrue(written.has(key), "first-launch file missing spec key: " + key);
        }
        assertEquals(20, config.maxTimeLapseRate);
    }

    @Test
    void missingKeysGetDefaults() {
        String json = """
                {
                  "configVersion": 1,
                  "enableTimeLapse": false,
                  "brewHasteSeconds": 120
                }
                """;

        RespiteConfig config = GSON.fromJson(json, RespiteConfig.class);

        assertFalse(config.enableTimeLapse);
        assertEquals(120, config.brewHasteSeconds);

        RespiteConfig defaults = new RespiteConfig();
        assertEquals(defaults.maxTimeLapseRate, config.maxTimeLapseRate);
        assertEquals(defaults.newMoonHealMultiplier, config.newMoonHealMultiplier);
        assertEquals(defaults.wearinessThresholdDays, config.wearinessThresholdDays);
        assertTrue(config.showTimeLapseMessages);
        assertTrue(config.showExhaustionBlink);
    }

    @Test
    void unknownKeysAreIgnored(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("respite.json");
        Files.writeString(path, """
                {
                  "configVersion": 1,
                  "someRetiredKey": 17,
                  "maxTimeLapseRate": 30
                }
                """);

        RespiteConfig config = RespiteConfig.load(path);

        assertEquals(30, config.maxTimeLapseRate);
        assertTrue(config.enableTimeLapse);
    }

    @Test
    void valuesBelowMinimumAreClamped() {
        RespiteConfig config = new RespiteConfig();
        config.maxTimeLapseRate = 1;
        config.timeLapseTickBudgetMs = 4;
        config.idleThresholdMinutes = 0;
        config.restfulHealIntervalTicks = 99;
        config.newMoonHealMultiplier = 0.5;
        config.phantomAltitudeMin = -65;
        config.wearinessThresholdDays = 0;
        config.wearinessRegenPenalty = -0.1;
        config.exhaustedThresholdDays = 1;
        config.exhaustedRegenPenalty = -1.0;
        config.wellRestedSeconds = -5;
        config.wellRestedRegenBonus = -0.1;
        config.brewHasteSeconds = -5;
        config.bedrollRestfulMultiplier = -0.1;

        config.clamp();

        assertEquals(2, config.maxTimeLapseRate);
        assertEquals(5, config.timeLapseTickBudgetMs);
        assertEquals(1, config.idleThresholdMinutes);
        assertEquals(100, config.restfulHealIntervalTicks);
        assertEquals(1.0, config.newMoonHealMultiplier);
        assertEquals(-64, config.phantomAltitudeMin);
        assertEquals(1, config.wearinessThresholdDays);
        assertEquals(0.0, config.wearinessRegenPenalty);
        // Range-clamped to 2, then raised to wearinessThresholdDays (1) + 1 = 2.
        assertEquals(2, config.exhaustedThresholdDays);
        assertEquals(0.0, config.exhaustedRegenPenalty);
        assertEquals(0, config.wellRestedSeconds);
        assertEquals(0.0, config.wellRestedRegenBonus);
        assertEquals(0, config.brewHasteSeconds);
        assertEquals(0.0, config.bedrollRestfulMultiplier);
    }

    @Test
    void valuesAboveMaximumAreClamped() {
        RespiteConfig config = new RespiteConfig();
        config.maxTimeLapseRate = 101;
        config.timeLapseTickBudgetMs = 46;
        config.idleThresholdMinutes = 61;
        config.restfulHealIntervalTicks = 2401;
        config.newMoonHealMultiplier = 4.1;
        config.phantomAltitudeMin = 321;
        config.wearinessThresholdDays = 31;
        config.wearinessRegenPenalty = 0.96;
        config.exhaustedThresholdDays = 61;
        config.exhaustedRegenPenalty = 2.0;
        config.wellRestedSeconds = 601;
        config.wellRestedRegenBonus = 2.1;
        config.brewHasteSeconds = 601;
        config.bedrollRestfulMultiplier = 1.1;

        config.clamp();

        assertEquals(100, config.maxTimeLapseRate);
        assertEquals(45, config.timeLapseTickBudgetMs);
        assertEquals(60, config.idleThresholdMinutes);
        assertEquals(2400, config.restfulHealIntervalTicks);
        assertEquals(4.0, config.newMoonHealMultiplier);
        assertEquals(320, config.phantomAltitudeMin);
        assertEquals(30, config.wearinessThresholdDays);
        assertEquals(0.95, config.wearinessRegenPenalty);
        assertEquals(60, config.exhaustedThresholdDays);
        assertEquals(0.95, config.exhaustedRegenPenalty);
        assertEquals(600, config.wellRestedSeconds);
        assertEquals(2.0, config.wellRestedRegenBonus);
        assertEquals(600, config.brewHasteSeconds);
        assertEquals(1.0, config.bedrollRestfulMultiplier);
    }

    @Test
    void boundaryValuesPassUnclamped() {
        RespiteConfig low = new RespiteConfig();
        low.maxTimeLapseRate = 2;
        low.timeLapseTickBudgetMs = 5;
        low.idleThresholdMinutes = 1;
        low.restfulHealIntervalTicks = 100;
        low.newMoonHealMultiplier = 1.0;
        low.phantomAltitudeMin = -64;
        low.wearinessThresholdDays = 1;
        low.wearinessRegenPenalty = 0.0;
        low.exhaustedThresholdDays = 2;
        low.exhaustedRegenPenalty = 0.0;
        low.wellRestedSeconds = 0;
        low.wellRestedRegenBonus = 0.0;
        low.brewHasteSeconds = 0;
        low.bedrollRestfulMultiplier = 0.0;

        low.clamp();

        assertEquals(2, low.maxTimeLapseRate);
        assertEquals(5, low.timeLapseTickBudgetMs);
        assertEquals(1, low.idleThresholdMinutes);
        assertEquals(100, low.restfulHealIntervalTicks);
        assertEquals(1.0, low.newMoonHealMultiplier);
        assertEquals(-64, low.phantomAltitudeMin);
        assertEquals(1, low.wearinessThresholdDays);
        assertEquals(0.0, low.wearinessRegenPenalty);
        assertEquals(2, low.exhaustedThresholdDays);
        assertEquals(0.0, low.exhaustedRegenPenalty);
        assertEquals(0, low.wellRestedSeconds);
        assertEquals(0.0, low.wellRestedRegenBonus);
        assertEquals(0, low.brewHasteSeconds);
        assertEquals(0.0, low.bedrollRestfulMultiplier);

        RespiteConfig high = new RespiteConfig();
        high.maxTimeLapseRate = 100;
        high.timeLapseTickBudgetMs = 45;
        high.idleThresholdMinutes = 60;
        high.restfulHealIntervalTicks = 2400;
        high.newMoonHealMultiplier = 4.0;
        high.phantomAltitudeMin = 320;
        high.wearinessThresholdDays = 30;
        high.wearinessRegenPenalty = 0.95;
        high.exhaustedThresholdDays = 60;
        high.exhaustedRegenPenalty = 0.95;
        high.wellRestedSeconds = 600;
        high.wellRestedRegenBonus = 2.0;
        high.brewHasteSeconds = 600;
        high.bedrollRestfulMultiplier = 1.0;

        high.clamp();

        assertEquals(100, high.maxTimeLapseRate);
        assertEquals(45, high.timeLapseTickBudgetMs);
        assertEquals(60, high.idleThresholdMinutes);
        assertEquals(2400, high.restfulHealIntervalTicks);
        assertEquals(4.0, high.newMoonHealMultiplier);
        assertEquals(320, high.phantomAltitudeMin);
        assertEquals(30, high.wearinessThresholdDays);
        assertEquals(0.95, high.wearinessRegenPenalty);
        assertEquals(60, high.exhaustedThresholdDays);
        assertEquals(0.95, high.exhaustedRegenPenalty);
        assertEquals(600, high.wellRestedSeconds);
        assertEquals(2.0, high.wellRestedRegenBonus);
        assertEquals(600, high.brewHasteSeconds);
        assertEquals(1.0, high.bedrollRestfulMultiplier);
    }

    @Test
    void exhaustedIsRaisedToAtLeastOneDayPastWeary() {
        RespiteConfig config = new RespiteConfig();
        config.wearinessThresholdDays = 10;
        config.exhaustedThresholdDays = 5;

        config.clamp();

        assertEquals(11, config.exhaustedThresholdDays);
    }

    @Test
    void exhaustedExactlyOneDayPastWearyIsUntouched() {
        RespiteConfig config = new RespiteConfig();
        config.wearinessThresholdDays = 10;
        config.exhaustedThresholdDays = 11;

        config.clamp();

        assertEquals(11, config.exhaustedThresholdDays);
    }

    @Test
    void exhaustedCrossClampAppliesAfterRangeClamp() {
        RespiteConfig config = new RespiteConfig();
        // Weariness clamps 100 -> 30 first, so Exhausted rises to 31, not 101.
        config.wearinessThresholdDays = 100;
        config.exhaustedThresholdDays = 2;

        config.clamp();

        assertEquals(30, config.wearinessThresholdDays);
        assertEquals(31, config.exhaustedThresholdDays);
    }

    @Test
    void corruptFileFallsBackToDefaultsAndIsLeftUntouched(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("respite.json");
        String corrupt = "{ this is not json";
        Files.writeString(path, corrupt);

        RespiteConfig config = RespiteConfig.load(path);

        RespiteConfig defaults = new RespiteConfig();
        assertEquals(defaults.maxTimeLapseRate, config.maxTimeLapseRate);
        assertEquals(defaults.enableTimeLapse, config.enableTimeLapse);
        assertEquals(corrupt, Files.readString(path), "a corrupt file must never be modified on disk");
    }

    @Test
    void nonObjectJsonFallsBackToDefaultsAndIsLeftUntouched(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("respite.json");
        String nonObject = "[1, 2, 3]";
        Files.writeString(path, nonObject);

        RespiteConfig config = RespiteConfig.load(path);

        assertEquals(new RespiteConfig().brewHasteSeconds, config.brewHasteSeconds);
        assertEquals(nonObject, Files.readString(path), "a non-object file must never be modified on disk");
    }

    @Test
    void outOfRangeValuesInFileAreClampedOnLoad(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("respite.json");
        Files.writeString(path, """
                {
                  "configVersion": 1,
                  "maxTimeLapseRate": 9999,
                  "wearinessRegenPenalty": 5.0
                }
                """);

        RespiteConfig config = RespiteConfig.load(path);

        assertEquals(100, config.maxTimeLapseRate);
        assertEquals(0.95, config.wearinessRegenPenalty);
    }

    @Test
    void saveThenLoadRoundTripsAndLeavesNoTmpFile(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("respite.json");
        RespiteConfig original = new RespiteConfig();
        original.enableTimeLapse = false;
        original.maxTimeLapseRate = 30;
        original.newMoonHealMultiplier = 3.5;
        original.showExhaustionBlink = false;

        original.save(path);
        RespiteConfig restored = RespiteConfig.load(path);

        assertFalse(restored.enableTimeLapse);
        assertEquals(30, restored.maxTimeLapseRate);
        assertEquals(3.5, restored.newMoonHealMultiplier);
        assertFalse(restored.showExhaustionBlink);
        assertFalse(Files.exists(dir.resolve("respite.json.tmp")), "atomic save must not leave a tmp file");
    }

    @Test
    void copyIsIndependentAndCarriesEveryValue() {
        RespiteConfig original = new RespiteConfig();
        original.enableTimeLapse = false;
        original.maxTimeLapseRate = 30;
        original.newMoonHealMultiplier = 3.5;
        original.showExhaustionBlink = false;

        RespiteConfig copy = original.copy();
        copy.maxTimeLapseRate = 99;

        assertFalse(copy.enableTimeLapse);
        assertEquals(3.5, copy.newMoonHealMultiplier);
        assertFalse(copy.showExhaustionBlink);
        assertEquals(1, copy.configVersion);
        assertEquals(30, original.maxTimeLapseRate, "mutating the copy must not touch the original");
    }

    @Test
    void versionlessFileIsStampedAndPersistedOnLoad(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("respite.json");
        Files.writeString(path, """
                {
                  "maxTimeLapseRate": 30
                }
                """);

        RespiteConfig config = RespiteConfig.load(path);

        assertEquals(30, config.maxTimeLapseRate);
        JsonObject written = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals(1, written.get("configVersion").getAsInt(), "the migrated schema must be persisted back");
        assertEquals(30, written.get("maxTimeLapseRate").getAsInt(), "existing tuning must be carried forward");
    }
}
