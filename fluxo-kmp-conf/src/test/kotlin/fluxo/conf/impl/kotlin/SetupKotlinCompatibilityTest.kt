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
            Triple(KotlinVersion(99, 0, 0), 99, 24),
            // 2.2 boundary — at + just below
            Triple(KotlinVersion(2, 2, 0), 99, 24),
            Triple(KotlinVersion(2, 1, 21), 99, 23),
            // 2.1 boundary — at + just below
            Triple(KotlinVersion(2, 1, 0), 99, 23),
            Triple(KotlinVersion(2, 0, 21), 99, 22),
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
            Triple(KotlinVersion(2, 2, 0), 24, 24),
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

    // region table-staleness warning predicate

    @Test
    fun `warning predicate fires only when Kotlin minor outruns the tabulated bracket`() {
        // The point of this seed: previous predicate was `v > LATEST_TABULATED_KOTLIN`
        // where `LATEST_TABULATED_KOTLIN = KOTLIN_2_0 = (2, 0, 0)`. KotlinVersion's
        // compareTo includes patch — so `2.0.21 > 2.0.0` was TRUE and the warning
        // false-fired on every consumer build with Kotlin 2.0.x patch ≥ 1. The
        // current `Int.toKotlinSupportedJvmMajorVersion` table covers Kotlin through
        // 2.2.x; only a NEW minor (2.3.x onward) needs the maintainer to extend it.
        //
        // Assertion shape: `v >= FIRST_UNTABULATED_KOTLIN`. Tested boundary cases
        // falsify both the off-by-one (2.2.21 must NOT warn) and the strict-vs-
        // non-strict comparison (2.3.0 MUST warn — it's the first untabulated minor).
        check(KotlinVersion(2, 0, 0) < FIRST_UNTABULATED_KOTLIN) {
            "Kotlin 2.0.0 must not trigger the table-staleness warning — it's tabulated"
        }
        check(KotlinVersion(2, 0, 21) < FIRST_UNTABULATED_KOTLIN) {
            "Kotlin 2.0.21 must not trigger the warning — patches live inside the 2.0 bracket"
        }
        check(KotlinVersion(2, 1, 0) < FIRST_UNTABULATED_KOTLIN) {
            "Kotlin 2.1.0 must not trigger the warning — 2.1 (JVM 23) is now tabulated"
        }
        check(KotlinVersion(2, 2, 21) < FIRST_UNTABULATED_KOTLIN) {
            "Kotlin 2.2.21 must not trigger the warning — 2.2 (JVM 24) is now tabulated"
        }
        check(KotlinVersion(2, 0, KotlinVersion.MAX_COMPONENT_VALUE) < FIRST_UNTABULATED_KOTLIN) {
            "Even the highest 2.0.x patch must not warn — bracket covers all patches"
        }
        check(KotlinVersion(2, 3, 0) >= FIRST_UNTABULATED_KOTLIN) {
            "Kotlin 2.3.0 MUST trigger the warning — first untabulated minor"
        }
        check(KotlinVersion(3, 0, 0) >= FIRST_UNTABULATED_KOTLIN) {
            "Kotlin 3.0.0 MUST trigger the warning — far beyond the table"
        }
    }

    // endregion
}
