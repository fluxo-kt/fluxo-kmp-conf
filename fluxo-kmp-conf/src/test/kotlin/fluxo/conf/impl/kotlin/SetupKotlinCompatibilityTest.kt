package fluxo.conf.impl.kotlin

import kotlin.KotlinVersion
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * RED-test seed for the pure-Kotlin surface of [SetupKotlinCompatibility].
 *
 * Scope is deliberately narrow: only the branches that don't reach into the
 * Kotlin Gradle DSL (`org.jetbrains.kotlin.gradle.dsl.KotlinVersion`), which is
 * `compileOnly` for the plugin and therefore unavailable on the test classpath.
 * The covered surfaces are still the highest-leverage ones — the JVM-target
 * table is the silent-cap bug class that S0f-1's one-shot warning was added to
 * mitigate, and the version-string parser is the reflective fallback when KGP
 * renames its plugin-version method.
 *
 * Each test falsifies one branch: a Kotlin upgrade that drops/renames an
 * entry or a KGP version-string format change will turn one of these GREEN
 * tests RED — pointing at the exact line in `SetupKotlinCompatibility.kt`
 * that needs updating instead of surfacing as a silent runtime cap.
 */
internal class SetupKotlinCompatibilityTest {

    // region toKotlinSupportedJvmMajorVersion — Kotlin → max-JVM table

    @Test
    fun `JVM target table caps at the documented max per Kotlin minor`() {
        // (Kotlin plugin version, requested JVM target, expected effective target).
        //
        // Two falsification dimensions per branch:
        //  1. AT-boundary  — proves the `>=` includes the boundary.
        //  2. JUST-BELOW   — proves the next-lower branch is selected; mutates
        //                    `>=` to `>` would silently flip these to the higher
        //                    cap and the test goes RED at the exact branch.
        // The high-end `99` case proves capping; an impossibly-high
        // `KotlinVersion(99, 0, 0)` proves the top branch still caps (no
        // accidental passthrough when a future Kotlin outruns the table).
        val cases = listOf(
            // top-branch saturation — table doesn't grow itself
            Triple(KotlinVersion(99, 0, 0), 99, 22),
            // 2.0 boundary — at + just below
            Triple(KotlinVersion(2, 0, 0), 99, 22),
            Triple(KotlinVersion(1, 9, 21), 99, 21),
            // 1.9.20 boundary — at + just below
            Triple(KotlinVersion(1, 9, 20), 99, 21),
            Triple(KotlinVersion(1, 9, 19), 99, 20),
            // 1.9.0 boundary — at + just below
            Triple(KotlinVersion(1, 9, 0), 99, 20),
            Triple(KotlinVersion(1, 8, 99), 99, 19),
            // 1.8.0 boundary — at + just below
            Triple(KotlinVersion(1, 8, 0), 99, 19),
            Triple(KotlinVersion(1, 7, 99), 99, 17),
            // 1.6.0 boundary — at + just below
            Triple(KotlinVersion(1, 6, 0), 99, 17),
            Triple(KotlinVersion(1, 5, 99), 99, 14),
            // 1.4.0 boundary — at + just below
            Triple(KotlinVersion(1, 4, 0), 99, 14),
            Triple(KotlinVersion(1, 3, 99), 99, 12),
            // 1.3.30 boundary — at + just below (falls to else)
            Triple(KotlinVersion(1, 3, 30), 99, 12),
            Triple(KotlinVersion(1, 3, 29), 99, 8),
            // else branch — pre-1.3.30
            Triple(KotlinVersion(1, 0, 0), 99, 8),
            // passthrough at-cap (no over-zealous capping)
            Triple(KotlinVersion(2, 0, 0), 22, 22),
            // passthrough below-floor (the >8 guard is the floor, not the table)
            Triple(KotlinVersion(1, 0, 0), 8, 8),
        )
        for ((kotlin, requested, expected) in cases) {
            assertEquals(
                expected = expected,
                actual = requested.toKotlinSupportedJvmMajorVersion(kotlin),
                message = "Kotlin $kotlin: requested=$requested expected cap=$expected",
            )
        }
    }

    // endregion

    // region parseKotlinPluginVersion — pre-release suffix tolerance

    @Test
    fun `parses stable plugin version strings`() {
        assertEquals(KotlinVersion(2, 0, 21), parseKotlinPluginVersion("2.0.21"))
        assertEquals(KotlinVersion(1, 9, 0), parseKotlinPluginVersion("1.9.0"))
        assertEquals(KotlinVersion(2, 1, 0), parseKotlinPluginVersion("2.1"))
    }

    @Test
    fun `parses pre-release suffixed plugin version strings`() {
        // KGP exposes RC/Beta/dev qualifiers via dash; we strip them.
        assertEquals(KotlinVersion(2, 1, 0), parseKotlinPluginVersion("2.1.0-RC2"))
        assertEquals(KotlinVersion(2, 3, 0), parseKotlinPluginVersion("2.3.0-Beta3"))
        assertEquals(KotlinVersion(2, 0, 21), parseKotlinPluginVersion("2.0.21-stable"))
    }

    // endregion
}
