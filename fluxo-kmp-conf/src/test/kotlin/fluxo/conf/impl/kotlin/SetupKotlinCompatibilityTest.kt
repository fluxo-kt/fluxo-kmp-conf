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
        // Each row is (Kotlin plugin version, requested JVM target, expected effective target).
        // The 99 case verifies the cap behaviour; the at-cap case verifies passthrough.
        val cases = listOf(
            Triple(KotlinVersion(2, 0, 0), 99, 22),
            Triple(KotlinVersion(2, 0, 0), 22, 22),
            Triple(KotlinVersion(1, 9, 20), 99, 21),
            Triple(KotlinVersion(1, 9, 0), 99, 20),
            Triple(KotlinVersion(1, 8, 0), 99, 19),
            Triple(KotlinVersion(1, 6, 0), 99, 17),
            Triple(KotlinVersion(1, 4, 0), 99, 14),
            Triple(KotlinVersion(1, 3, 30), 99, 12),
            Triple(KotlinVersion(1, 3, 0), 99, 8),
        )
        for ((kotlin, requested, expected) in cases) {
            assertEquals(
                expected = expected,
                actual = requested.toKotlinSupportedJvmMajorVersion(kotlin),
                message = "Kotlin $kotlin: requested=$requested expected cap=$expected",
            )
        }
    }

    @Test
    fun `JVM targets at or below 8 pass through unchanged`() {
        // 8 is the historic floor; below 8 should never be capped.
        for (target in 1..8) {
            assertEquals(
                expected = target,
                actual = target.toKotlinSupportedJvmMajorVersion(KotlinVersion(1, 3, 0)),
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
