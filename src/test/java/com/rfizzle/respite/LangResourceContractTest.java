// Tier: 1 (pure JUnit)
package com.rfizzle.respite;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rfizzle.respite.config.RespiteConfig;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Pins concord {@code design/DESIGN-SYSTEM.md} §10 — the localization-key
 * prefix vocabulary and its casing rule — over the whole shipped lang file.
 *
 * <p>§10 casts casing by <em>surface</em>, not by mod: registry-derived keys are
 * snake_case because they mirror a registry id, {@code config.<mod>.<field>}
 * labels and their {@code .tooltip} pairs are camelCase because they mirror the
 * Java field they label, and every other authored surface is snake_case. A
 * blanket rename in either direction is therefore wrong; this guard is what
 * keeps a well-meaning one from landing.
 *
 * <p>Reads the classpath first so a lang edit invalidates this task — a
 * path-only read is invisible to Gradle's up-to-date checks, and the guard would
 * go quiet exactly when it should fire. The path is the IDE-runner fallback.
 */
class LangResourceContractTest {

    private static final String RESOURCE = "/assets/respite/lang/en_us.json";
    private static final Path SOURCE = Path.of("src/main/resources/assets/respite/lang/en_us.json");

    /** Vanilla-mandated prefixes: the key mirrors a registry id, so it is snake_case, not ours to rule. */
    private static final Set<String> REGISTRY_DERIVED = Set.of(
            "block", "item", "entity", "effect", "enchantment", "attribute", "death", "container");

    /**
     * Every §10 surface respite is allowed to author on, registry-derived rows included.
     *
     * <p>{@code fragment} is a §10 row in its own right, not an exception: a shared value
     * fragment — a localized 12-hour clock, a moon-phase name — is nested as a {@code %s}
     * argument into keys on two surfaces at once ({@code notification.respite.chronometer*}
     * on the action bar and {@code tooltip.respite.chronometer*} in Jade/WTHIT; see
     * {@code ChronometerLines}), so it has no surface of its own and any single surface
     * prefix would be false on the other. Ruled in concord's DESIGN-SYSTEM §10.
     */
    private static final Set<String> KNOWN_PREFIXES = Set.of(
            "config", "command", "hud", "gui", "tooltip", "message", "notification",
            "advancements", "info", "key", "stat", "itemGroup", "subtitles", "fragment",
            "block", "item", "entity", "effect", "enchantment", "attribute", "death", "container");

    /**
     * No off-table prefixes remain: {@code time.*} and {@code moon.*} moved to
     * {@code fragment.respite.*} once §10 gained the row. Kept as an empty set so a
     * <em>new</em> off-table prefix still fails loudly rather than being quietly added here.
     */
    private static final Set<String> TRACKED_OFF_TABLE_PREFIXES = Set.of();

    private static final Pattern SNAKE_SEGMENT = Pattern.compile("[a-z0-9]+(_[a-z0-9]+)*");
    private static final Pattern CAMEL_SEGMENT = Pattern.compile("[a-z][a-zA-Z0-9]*");

    private static JsonObject lang() {
        try (InputStream in = LangResourceContractTest.class.getResourceAsStream(RESOURCE)) {
            String json = in != null
                    ? new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    : Files.readString(SOURCE, StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not load " + RESOURCE, e);
        }
    }

    /** The config screen's fields — the keys §10 rules camelCase because they mirror a Java field. */
    private static Set<String> configFields() {
        Set<String> fields = new TreeSet<>();
        for (Field field : RespiteConfig.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            fields.add(field.getName());
        }
        return fields;
    }

    /** A {@code config.respite.<field>} label or its {@code .tooltip} pair. */
    private static boolean isConfigFieldLabel(String key, Set<String> fields) {
        if (!key.startsWith("config.respite.")) return false;
        String rest = key.substring("config.respite.".length());
        if (rest.endsWith(".tooltip")) {
            rest = rest.substring(0, rest.length() - ".tooltip".length());
        }
        return fields.contains(rest);
    }

    @Test
    void everyKeyUsesAKnownSurfacePrefix() {
        Set<String> unknown = new TreeSet<>();
        for (String key : lang().keySet()) {
            String prefix = key.substring(0, key.indexOf('.') < 0 ? key.length() : key.indexOf('.'));
            if (!KNOWN_PREFIXES.contains(prefix) && !TRACKED_OFF_TABLE_PREFIXES.contains(prefix)) {
                unknown.add(prefix);
            }
        }
        assertTrue(unknown.isEmpty(),
                "lang keys use prefixes outside the DESIGN-SYSTEM §10 vocabulary: " + unknown
                        + " — pick the surface the string is shown on, or take a new row to the hub");
    }

    @Test
    void configFieldLabelsAreCamelCase() {
        Set<String> fields = configFields();
        List<String> offenders = new ArrayList<>();
        for (String key : lang().keySet()) {
            if (!isConfigFieldLabel(key, fields)) continue;
            String rest = key.substring("config.respite.".length());
            if (rest.endsWith(".tooltip")) {
                rest = rest.substring(0, rest.length() - ".tooltip".length());
            }
            if (!CAMEL_SEGMENT.matcher(rest).matches()) {
                offenders.add(key);
            }
        }
        assertTrue(offenders.isEmpty(),
                "config field labels mirror their Java field and must be camelCase (§10): " + offenders);
    }

    @Test
    void everyOtherAuthoredSurfaceIsSnakeCase() {
        Set<String> fields = configFields();
        List<String> offenders = new ArrayList<>();
        for (String key : lang().keySet()) {
            // Registry-derived keys mirror a snake_case registry id, and config field
            // labels mirror a camelCase Java field. Everything else — including
            // config.respite.category.*, config.respite.title, and the read-only
            // server note, none of which name a field — is snake_case.
            if (isConfigFieldLabel(key, fields)) continue;
            String[] segments = key.split("\\.");
            for (int i = 0; i < segments.length; i++) {
                // Skip the prefix (a surface name like itemGroup is not ours to case)
                // and the mod-id segment.
                if (i == 0 || "respite".equals(segments[i])) continue;
                if (!SNAKE_SEGMENT.matcher(segments[i]).matches()) {
                    offenders.add(key);
                    break;
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "authored lang surfaces are snake_case (§10), these are not: " + offenders);
    }

    @Test
    void registryDerivedKeysStaySnakeCase() {
        // Called out separately from the sweep above: these are vanilla-mandated, so a
        // failure here is a broken translation, not a style nit — the key must match the
        // registry id exactly or the name never resolves in game.
        List<String> offenders = new ArrayList<>();
        for (String key : lang().keySet()) {
            int dot = key.indexOf('.');
            if (dot < 0 || !REGISTRY_DERIVED.contains(key.substring(0, dot))) continue;
            String id = key.substring(key.lastIndexOf('.') + 1);
            if (!SNAKE_SEGMENT.matcher(id).matches()) {
                offenders.add(key);
            }
        }
        assertTrue(offenders.isEmpty(),
                "registry-derived lang keys mirror a snake_case registry id: " + offenders);
    }
}
