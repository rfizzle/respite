/**
 * Respite's public API — the only package other mods should reference.
 *
 * <p>Everything in this package is <strong>stable</strong> per the
 * <a href="https://github.com/rfizzle/concord/blob/master/API-STANDARD.md">Concord
 * API Standard v1</a>: signatures here survive minor and patch releases, and a
 * breaking change requires a major version bump plus a changelog entry naming
 * the broken signature. Everything <em>outside</em> this package — engines,
 * handlers, registries, mixins — is internal and may change without notice in
 * any release.
 *
 * <p>Every type here carries the suite's stability marker,
 * {@link com.rfizzle.respite.api.Stable}. It is a local annotation rather than
 * {@code @ApiStatus.Stable} (which has no member in
 * {@code org.jetbrains.annotations}); per the suite's no-shared-jar rule each
 * mod declares its own marker in its {@code api} package.
 *
 * <p>Reads are server-authoritative. Respite has no HUD slot by design, so this
 * surface ships no HUD accessors.
 *
 * <p>Consume as a soft dependency only: compile against the mod with
 * {@code modCompileOnly} and guard every call site with
 * {@code FabricLoader.getInstance().isModLoaded("respite")}. See the
 * "For developers" section of the README for worked examples.
 */
package com.rfizzle.respite.api;
