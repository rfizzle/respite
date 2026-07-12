package com.rfizzle.respite.command;

import com.rfizzle.respite.config.RespiteConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The reflective field diff behind {@code /respite reload}'s "what changed"
 * report: the names of every {@link RespiteConfig} field whose value differs
 * between the config before the reload and the config after. {@code configVersion}
 * is excluded — it is schema bookkeeping, not a tunable a reader edited.
 *
 * <p>Pure (no {@code net.minecraft} types), reflective over the config's flat
 * public-field shape exactly as {@code ConfigLangContractTest} walks it, so it
 * stays in step with the config automatically and unit-tests without a bootstrap.
 */
public final class ConfigDiff {

    private ConfigDiff() {
    }

    /**
     * The names of the config fields that changed between {@code before} and
     * {@code after}, in declaration order.
     */
    public static List<String> changedFields(RespiteConfig before, RespiteConfig after) {
        List<String> changed = new ArrayList<>();
        for (Field field : RespiteConfig.class.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (Modifier.isStatic(mods) || field.isSynthetic() || "configVersion".equals(field.getName())) {
                continue;
            }
            try {
                if (!Objects.equals(field.get(before), field.get(after))) {
                    changed.add(field.getName());
                }
            } catch (IllegalAccessException e) {
                // A public instance field is always accessible; ignore defensively.
            }
        }
        return changed;
    }
}
