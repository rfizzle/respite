// Tier: 1 (pure JUnit)
package com.rfizzle.respite.command;

import com.rfizzle.respite.config.RespiteConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reflective config diff behind {@code /respite reload}'s "what changed"
 * report. Pure — {@link ConfigDiff} touches no {@code net.minecraft} types.
 */
class ConfigDiffTest {

    @Test
    void identicalConfigsReportNoChanges() {
        assertTrue(ConfigDiff.changedFields(new RespiteConfig(), new RespiteConfig()).isEmpty());
    }

    @Test
    void changedFieldsAreNamed() {
        RespiteConfig before = new RespiteConfig();
        RespiteConfig after = new RespiteConfig();
        after.maxTimeLapseRate = before.maxTimeLapseRate + 5;
        after.enableChronometer = !before.enableChronometer;
        after.newMoonHealMultiplier = before.newMoonHealMultiplier + 1.0;

        List<String> changed = ConfigDiff.changedFields(before, after);
        assertTrue(changed.contains("maxTimeLapseRate"));
        assertTrue(changed.contains("enableChronometer"));
        assertTrue(changed.contains("newMoonHealMultiplier"));
        assertEquals(3, changed.size(), "only the three edited fields should be reported");
    }

    @Test
    void configVersionIsNotReported() {
        RespiteConfig before = new RespiteConfig();
        RespiteConfig after = new RespiteConfig();
        after.configVersion = before.configVersion + 1;
        assertTrue(ConfigDiff.changedFields(before, after).isEmpty(),
                "configVersion is schema bookkeeping, not a tunable a reader edited");
    }
}
