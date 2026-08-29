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
 * — which is why every method reaches for {@code getResource*} first.
 *
 * <p>Paths are classpath paths, leading slash included:
 * {@code /assets/respite/lang/en_us.json}.
 */
public final class ShippedResources {

    /** Where the fallback reads from when the classpath has no copy. */
    private static final Path SOURCE_ROOT = Path.of("src/main/resources");

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

    private static Path fallback(String resource) {
        return SOURCE_ROOT.resolve(resource.startsWith("/") ? resource.substring(1) : resource);
    }
}
