// Tier: 1 (pure JUnit)
package com.rfizzle.respite.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The one loader every {@code *ResourceContractTest} reads shipped resources
 * through: <strong>classpath first</strong>, with a source-path fallback.
 *
 * <p>Reading the classpath is not a style preference — it is what makes these
 * guards fire. Gradle's up-to-date checks track the test task's classpath, so a
 * resource edit invalidates a classpath-reading test and reruns it. A test that
 * reaches for {@code src/main/resources} by path is invisible to that tracking:
 * the resource changes, the task stays {@code UP-TO-DATE}, and the guard goes
 * quiet exactly when it should fire. It is also the artifact under test —
 * {@code build/resources/main} is what the jar is built from, so a resource that
 * {@code processResources} excludes is correctly invisible here.
 *
 * <p>The path fallback exists for IDE runners that launch without the processed
 * resources directory on the classpath. It is a fallback, never the primary read
 * — which is why every method reaches for {@code getResource*} first. It searches
 * both {@code main} resource roots in {@code build.gradle} source-set order:
 * hand-authored {@code src/main/resources} and datagen's {@code src/main/generated}.
 * {@code processResources} merges the two into one {@code build/resources/main}
 * tree, so the classpath read cannot tell them apart and neither should this.
 *
 * <p>Paths are classpath paths, leading slash included:
 * {@code /assets/respite/lang/en_us.json}.
 */
public final class ShippedResources {

    /** Where the fallback reads from when the classpath has no copy, in source-set order. */
    private static final List<Path> SOURCE_ROOTS =
            List.of(Path.of("src/main/resources"), Path.of("src/main/generated"));

    private ShippedResources() {
    }

    /** The resource's text, classpath first. */
    public static String text(String resource) {
        try (InputStream in = ShippedResources.class.getResourceAsStream(resource)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            return Files.readString(fallback(resource), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read shipped resource " + resource, e);
        }
    }

    /** The resource parsed as a JSON object, classpath first. */
    public static JsonObject json(String resource) {
        return JsonParser.parseString(text(resource)).getAsJsonObject();
    }

    /** Whether the resource ships, classpath first. */
    public static boolean exists(String resource) {
        URL url = ShippedResources.class.getResource(resource);
        return url != null || Files.exists(fallback(resource));
    }

    /**
     * The first source root that holds the resource, or the first root — so a
     * genuinely absent resource still reports a path in its failure rather than
     * an empty Optional.
     */
    private static Path fallback(String resource) {
        String relative = resource.startsWith("/") ? resource.substring(1) : resource;
        return SOURCE_ROOTS.stream()
                .map(root -> root.resolve(relative))
                .filter(Files::exists)
                .findFirst()
                .orElseGet(() -> SOURCE_ROOTS.get(0).resolve(relative));
    }
}
