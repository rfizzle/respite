// Tier: 1 (pure JUnit)
package com.rfizzle.respite.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the boundary between what ships and what only ever serves a test.
 *
 * <p>Gametest fixtures — structure templates and bespoke loot tables — resolve
 * through the merged {@code ResourceManager} by namespace, so they work from any
 * loaded mod's resource root. That means they belong in the gametest source set,
 * whose manifest declares a separate {@code respite-gametest} mod that never
 * enters the jar.
 *
 * <p>Keeping them out of the shipped roots is worth a guard on two counts. A
 * loot table in the {@code respite} namespace is eagerly parsed and validated on
 * every datapack reload on every server, including the integrated server behind
 * a singleplayer world, purely to serve a test. A structure template is cheaper
 * — it loads on demand rather than on reload — but it is still listed for
 * {@code /place template} autocomplete, so it surfaces test fixtures to
 * operators as if they were content.
 *
 * <p>These assertions read the <strong>test classpath</strong> rather than the
 * source tree, because the classpath is what the jar is built from — it is the
 * shipped artifact under test. Run against a clean build: a stale
 * {@code build/resources/} tree can still hold fixtures that were relocated in
 * source, which reads as a live leak a rebuild makes disappear.
 */
class ShippedResourceHygieneTest {

    /**
     * A shipped resource root, located by anchoring on a file known to live at
     * its top level.
     *
     * @param anchor  classpath path of the anchor file
     * @param markers entries that must exist directly under the resolved root,
     *                so that an anchor which moves into a subdirectory fails
     *                loudly instead of silently narrowing the walk to that
     *                subdirectory
     */
    private record ShippedRoot(String anchor, List<String> markers) {
    }

    /**
     * Respite ships one resource root. {@code splitEnvironmentSourceSets()} is
     * on, but {@code src/client/resources} does not exist — a source set with no
     * resources contributes no anchor and belongs out of this list. The count is
     * asserted below so adding one later cannot leave it quietly unscanned.
     *
     * <p>Anchored on the mixin config rather than {@code fabric.mod.json}: both
     * source sets would ship a file by the latter name, so it is the weaker
     * anchor of the two.
     */
    private static final List<ShippedRoot> SHIPPED_ROOTS = List.of(
            new ShippedRoot("/respite.mixins.json",
                    List.of("fabric.mod.json", "respite.mixins.json", "assets/respite", "data/respite")));

    /** Path segment that marks a file as existing only to serve the gametest suite. */
    private static final String TEST_ONLY_SEGMENT = "gametest";

    /**
     * Structure templates are a gametest-only format here — respite ships no
     * structures of its own — so the extension is disqualifying wherever it
     * appears, not just under a {@code gametest/} directory.
     */
    private static final String TEMPLATE_EXTENSION = ".snbt";

    private static final Path GAMETEST_RESOURCES = Path.of("src/gametest/resources");

    /**
     * Every gametest-only resource, derived from the gametest source set rather
     * than pinned as a hardcoded list — respite has no fixtures today, and a
     * hardcoded empty list is a guard that can never fail. The gametest
     * manifest is excluded: the shipped root legitimately holds a file of that
     * name, and it is the registration guard that keeps the two apart.
     */
    private static List<String> gametestOnlyResources() {
        List<String> fixtures = new ArrayList<>();
        if (!Files.isDirectory(GAMETEST_RESOURCES)) {
            return fixtures;
        }
        try (Stream<Path> tree = Files.walk(GAMETEST_RESOURCES)) {
            tree.filter(Files::isRegularFile)
                    .map(GAMETEST_RESOURCES::relativize)
                    .filter(relative -> !"fabric.mod.json".equals(relative.toString()))
                    .forEach(relative -> {
                        List<String> parts = new ArrayList<>();
                        relative.forEach(part -> parts.add(part.toString()));
                        fixtures.add("/" + String.join("/", parts));
                    });
        } catch (IOException e) {
            throw new AssertionError("could not walk " + GAMETEST_RESOURCES, e);
        }
        fixtures.sort(String::compareTo);
        return fixtures;
    }

    /** Every gametest-only resource must be absent from the shipped classpath. */
    @Test
    void gametestFixtures_areNotOnTheShippedClasspath() {
        // Resolving the roots first keeps this from passing vacuously against an
        // empty classpath — a guard that cannot fail is worse than no guard,
        // because it reads as coverage.
        shippedResourceRoots();

        for (String fixture : gametestOnlyResources()) {
            assertNull(ShippedResourceHygieneTest.class.getResource(fixture),
                    "test-only fixture is on the shipped classpath and will land in the jar: "
                            + fixture + " — it belongs in src/gametest/resources/");
        }
    }

    /** The general form of the same rule, so a fixture added under a new name is caught too. */
    @Test
    void noGametestPathSegment_anywhereInShippedResources() {
        List<String> offenders = scanShippedRoots(
                relative -> hasSegment(relative, TEST_ONLY_SEGMENT));

        assertTrue(offenders.isEmpty(),
                "shipped resources must not contain gametest-only files, but found "
                        + offenders.size() + ": " + offenders
                        + " — move them to src/gametest/resources/, which is on the "
                        + "runGametest classpath but never enters the jar");
    }

    /** Catches a template parked outside a {@code gametest/} directory, which the sweep above misses. */
    @Test
    void noStructureTemplates_anywhereInShippedResources() {
        List<String> offenders = scanShippedRoots(
                relative -> relative.getFileName().toString().endsWith(TEMPLATE_EXTENSION));

        assertTrue(offenders.isEmpty(),
                "shipped resources must not contain structure templates, but found "
                        + offenders.size() + ": " + offenders
                        + " — templates serve the gametest suite only and belong in "
                        + "src/gametest/resources/");
    }

    /** Walks every shipped root, collecting root-relative paths of files matching the rule. */
    private static List<String> scanShippedRoots(Predicate<Path> offending) {
        List<String> offenders = new ArrayList<>();

        for (Path root : shippedResourceRoots()) {
            try (Stream<Path> tree = Files.walk(root)) {
                tree.filter(Files::isRegularFile)
                        .map(root::relativize)
                        .filter(offending)
                        .map(Path::toString)
                        .forEach(offenders::add);
            } catch (Exception e) {
                fail("could not walk shipped resource root " + root, e);
            }
        }

        offenders.sort(String::compareTo);
        return offenders;
    }

    private static boolean hasSegment(Path relative, String segment) {
        for (Path part : relative) {
            if (segment.equals(part.toString())) {
                return true;
            }
        }
        return false;
    }

    /** Resolves every directory the shipped resources are processed into. */
    private static List<Path> shippedResourceRoots() {
        List<Path> roots = new ArrayList<>();

        for (ShippedRoot shipped : SHIPPED_ROOTS) {
            URL anchor = ShippedResourceHygieneTest.class.getResource(shipped.anchor());
            if (anchor == null) {
                fail("could not locate " + shipped.anchor() + " on the test classpath — the "
                        + "anchor this guard uses to find a shipped resource root has moved");
            }
            if (!"file".equals(anchor.getProtocol())) {
                fail("expected the shipped resource root to be a directory on the test "
                        + "classpath, but " + shipped.anchor() + " resolved to " + anchor);
            }

            Path root;
            try {
                root = Path.of(anchor.toURI()).getParent();
            } catch (Exception e) {
                return fail("could not resolve a filesystem path for " + anchor, e);
            }

            for (String marker : shipped.markers()) {
                assertTrue(Files.exists(root.resolve(marker)),
                        "resolved shipped resource root " + root + " is missing expected entry '"
                                + marker + "' — the anchor " + shipped.anchor() + " has most "
                                + "likely moved into a subdirectory, which would silently narrow "
                                + "this guard's coverage");
            }

            roots.add(root);
        }

        assertEquals(SHIPPED_ROOTS.size(), roots.size(),
                "every shipped resource root must resolve — an unscanned root is a hole");
        return roots;
    }
}
