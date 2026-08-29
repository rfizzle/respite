// Tier: 1 (pure JUnit)
package com.rfizzle.respite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Tier-1 guard that the gametest suites on disk and the {@code fabric-gametest}
 * entrypoints in the companion manifest stay in lockstep (mc-mod-testing).
 *
 * <p>Registration fails silently in <em>both</em> directions: an unregistered
 * {@code FabricGameTest} never runs and never warns, so a suite can rot for
 * months while CI stays green; a stale entrypoint naming a deleted class crashes
 * the run at startup. Respite carried no guard at all until this one — a sweep
 * of 15 suites that could have gone quiet without a red build.
 *
 * <p>The gametest source set is not on the test classpath, so its classes cannot
 * be enumerated reflectively — the guard reads the source tree instead, walking
 * it recursively so suites in subpackages are not missed. A suite is identified
 * by the <em>interface it implements</em>, not a filename suffix and not an
 * annotation regex: the source set also holds helpers ({@code MockPlayers}), so
 * a suffix-free match would flag those as unregistered, a suffix-only match
 * would let a suite named {@code FooTests} slip past both sides of the
 * comparison at once, and an unanchored {@code @GameTest} regex matches the
 * annotation inside a comment or a string. {@code implements FabricGameTest} is
 * the same predicate the loader itself uses.
 *
 * <p>The task inputs this reads are declared in {@code build.gradle} — Gradle
 * sees no dependency between the test task and a source tree it never compiles,
 * so without that block this check would report {@code UP-TO-DATE} exactly when
 * registration had drifted.
 */
class GametestRegistrationTest {

    private static final Path GAMETEST_SOURCES = Path.of("src/gametest/java");
    private static final Path GAMETEST_MANIFEST = Path.of("src/gametest/resources/fabric.mod.json");
    private static final Path SHIPPED_MANIFEST = Path.of("src/main/resources/fabric.mod.json");

    /** Matches a class's {@code implements} clause naming FabricGameTest. */
    private static final Pattern IMPLEMENTS_FABRIC_GAMETEST =
            Pattern.compile("implements\\s+[^{]*\\bFabricGameTest\\b");

    /** Fully-qualified names of every class under the gametest tree, mapped to its source text. */
    private static TreeMap<String, String> gametestSources() {
        TreeMap<String, String> sources = new TreeMap<>();
        try (Stream<Path> tree = Files.walk(GAMETEST_SOURCES)) {
            tree.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                String relative = GAMETEST_SOURCES.relativize(p).toString();
                String className = relative.substring(0, relative.length() - ".java".length())
                        .replace(File.separatorChar, '.');
                try {
                    sources.put(className, Files.readString(p, StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new AssertionError("could not walk " + GAMETEST_SOURCES, e);
        } catch (UncheckedIOException e) {
            throw new AssertionError("could not read a source file under " + GAMETEST_SOURCES, e.getCause());
        }
        return sources;
    }

    private static boolean isSuite(String source) {
        return IMPLEMENTS_FABRIC_GAMETEST.matcher(source).find();
    }

    private static Set<String> suitesOnDisk() {
        TreeSet<String> suites = new TreeSet<>();
        gametestSources().forEach((className, source) -> {
            if (isSuite(source)) {
                suites.add(className);
            }
        });
        return suites;
    }

    private static Set<String> declaredEntrypoints() {
        JsonObject entrypoints = readEntrypoints(GAMETEST_MANIFEST);
        JsonArray entries = entrypoints.getAsJsonArray("fabric-gametest");
        assertNotNull(entries, GAMETEST_MANIFEST + " declares no fabric-gametest entrypoints"
                + " — every gametest suite would silently stop running");
        TreeSet<String> declared = new TreeSet<>();
        for (JsonElement entry : entries) {
            declared.add(entry.getAsString());
        }
        return declared;
    }

    @Test
    void everySuiteOnDiskIsRegistered() {
        TreeSet<String> unregistered = new TreeSet<>(suitesOnDisk());
        unregistered.removeAll(declaredEntrypoints());
        assertTrue(unregistered.isEmpty(),
                "gametest suites exist but are not declared in " + GAMETEST_MANIFEST
                        + " — they will silently never run: " + unregistered);
    }

    @Test
    void everyRegisteredEntrypointIsASuiteOnDisk() {
        TreeSet<String> dangling = new TreeSet<>(declaredEntrypoints());
        dangling.removeAll(suitesOnDisk());
        assertTrue(dangling.isEmpty(),
                GAMETEST_MANIFEST + " declares entrypoints that are not FabricGameTest classes on"
                        + " disk — the gametest run will fail to load them: " + dangling);
    }

    @Test
    void suiteNamingConventionHoldsInBothDirections() {
        // Matching suites by interface closes the "helper flagged as unregistered" hole;
        // enforcing the name closes the other one, where a suite called FooTests goes
        // missing from the source-tree scan and the manifest at the same time and the
        // guards above stay green.
        TreeSet<String> misnamedSuites = new TreeSet<>();
        TreeSet<String> impostors = new TreeSet<>();
        gametestSources().forEach((className, source) -> {
            boolean suite = isSuite(source);
            boolean named = className.endsWith("GameTest");
            if (suite && !named) {
                misnamedSuites.add(className);
            } else if (!suite && named) {
                impostors.add(className);
            }
        });
        assertTrue(misnamedSuites.isEmpty(),
                "FabricGameTest implementors must be named *GameTest: " + misnamedSuites);
        assertTrue(impostors.isEmpty(),
                "classes named *GameTest must implement FabricGameTest: " + impostors);
    }

    @Test
    void shippedManifestDeclaresNoGametestEntrypoints() {
        // fabric-gametest-api-v1's main entrypoint is ungated: on every server launch it
        // instantiates each declared fabric-gametest class. The gametest classes are only
        // on the gametest run classpath, so declaring them in the shipped manifest crashes
        // runServer and runDatagen outright.
        JsonObject entrypoints = readEntrypoints(SHIPPED_MANIFEST);
        assertFalse(entrypoints.has("fabric-gametest"),
                "fabric-gametest entrypoints belong in " + GAMETEST_MANIFEST
                        + ", not the shipped manifest");
    }

    @Test
    void gametestManifestDependsOnExactlyTheMainMod() {
        // Set equality, not containment: the loader, Minecraft, Java, and Fabric API floors
        // are enforced transitively — this mod cannot load unless respite did, and respite
        // declares them itself. Restating one here makes every toolchain bump a two-file
        // edit whose missed half fails only under runGametest, as a confusing load error.
        // A containment check would pass while exactly that stale floor rotted in place.
        assertEquals(Set.of("respite"), declaredDependencies(GAMETEST_MANIFEST),
                GAMETEST_MANIFEST + " must depend on the main mod alone");
    }

    private static Set<String> declaredDependencies(Path path) {
        JsonObject root = readJson(path);
        assertTrue(root.has("depends"), path + " has no \"depends\" object");
        assertTrue(root.get("depends").isJsonObject(), path + " has a non-object \"depends\"");
        return new TreeSet<>(root.getAsJsonObject("depends").keySet());
    }

    private static JsonObject readEntrypoints(Path path) {
        JsonObject root = readJson(path);
        assertTrue(root.has("entrypoints"), path + " has no \"entrypoints\" object");
        return root.getAsJsonObject("entrypoints");
    }

    private static JsonObject readJson(Path path) {
        try {
            return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }
}
