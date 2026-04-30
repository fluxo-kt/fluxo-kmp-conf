@file:Suppress("MagicNumber")

package fluxo.conf.impl.kotlin

import kotlin.KotlinVersion

/**
 * Pure-Kotlin version-table surface, separated from [SetupKotlinCompatibility]
 * so it can be exercised by unit tests without dragging the KGP `compileOnly`
 * classpath into the test runtime.
 *
 * Only symbols that have **zero** transitive references to
 * `org.jetbrains.kotlin.gradle.*` belong in this file. Adding a KGP-DSL ref
 * here re-couples the file-class `<clinit>` to KGP and breaks the unit tests
 * with `NoClassDefFoundError` — keep the discipline.
 */

private val KOTLIN_1_3_30 = KotlinVersion(1, 3, 30)

internal val KOTLIN_1_4 = KotlinVersion(1, 4)

private val KOTLIN_1_6 = KotlinVersion(1, 6)

internal val KOTLIN_1_7 = KotlinVersion(1, 7)

internal val KOTLIN_1_8 = KotlinVersion(1, 8)

internal val KOTLIN_1_8_20 = KotlinVersion(1, 8, 20)

internal val KOTLIN_1_9 = KotlinVersion(1, 9)

internal val KOTLIN_1_9_20 = KotlinVersion(1, 9, 20)

internal val KOTLIN_2_0 = KotlinVersion(2, 0, 0)

internal val KOTLIN_2_0_20 = KotlinVersion(2, 0, 20)

internal val KOTLIN_2_1 = KotlinVersion(2, 1, 0)

internal val KOTLIN_2_2 = KotlinVersion(2, 2, 0)

private val KOTLIN_2_3 = KotlinVersion(2, 3, 0)

// First Kotlin minor that is NOT yet represented in the JVM-target compatibility
// table at `Int.toKotlinSupportedJvmMajorVersion` below. Bump in lockstep with
// the table entries — drives a one-shot warning so the maintainer notices an
// upstream Kotlin runtime overrunning the table (silent JVM-target capping was
// the pre-existing failure mode).
//
// Compared with `>=`, NOT `KotlinVersion`'s lexicographic `compareTo` against
// `KOTLIN_2_2` (which would treat any 2.2.x patch ≥ 1 as "beyond" because patch
// is part of the comparison key — false-positive on every consumer build).
internal val FIRST_UNTABULATED_KOTLIN = KOTLIN_2_3

@Volatile
internal var WARNED_KOTLIN_BEYOND_TABLE = false

@Volatile
internal var KOTLIN_PLUGIN_VERSION: KotlinVersion = KotlinVersion.CURRENT

@Volatile
internal var KOTLIN_PLUGIN_VERSION_STRING: String = KOTLIN_PLUGIN_VERSION.toString()

/**
 * Parses a Kotlin plugin version string into a [KotlinVersion].
 *
 * Tolerates pre-release suffixes (`2.1.0-RC2`, `2.3.0-Beta3`, `2.0.21-stable`)
 * and missing patch (`2.1` → patch = 0). Extracted so it's unit-testable
 * without going through KGP — the production caller is fragile to KGP renames,
 * so a pure-string fallback path is the safety net.
 */
internal fun parseKotlinPluginVersion(versionString: String): KotlinVersion {
    val baseVersion = versionString.split("-", limit = 2)[0]
    val parts = baseVersion.split(".")
    return KotlinVersion(
        major = parts[0].toInt(),
        minor = parts[1].toInt(),
        patch = parts.getOrNull(2)?.toInt() ?: 0,
    )
}

internal fun Int.toKotlinSupportedJvmMajorVersion(
    pluginVersion: KotlinVersion = KOTLIN_PLUGIN_VERSION,
): Int {
    // Align with the current Kotlin plugin supported JVM targets
    if (this > 8) {
        val maxSupportedTarget = when {
            // 2.2.0 added support for 24
            // https://kotlinlang.org/docs/whatsnew22.html#kotlin-jvm
            pluginVersion >= KOTLIN_2_2 -> 24
            // 2.1.0 added support for 23
            // https://kotlinlang.org/docs/whatsnew21.html#kotlin-jvm
            pluginVersion >= KOTLIN_2_1 -> 23
            // 2.0.0 added support for 22
            // https://kotlinlang.org/docs/whatsnew20.html#kotlin-jvm
            pluginVersion >= KOTLIN_2_0 -> 22
            // 1.9.20 added support for 21
            // https://kotlinlang.org/docs/whatsnew1920.html#kotlin-jvm
            pluginVersion >= KOTLIN_1_9_20 -> 21
            // 1.9.0 added support for 20
            // https://kotlinlang.org/docs/whatsnew19.html#kotlin-jvm
            pluginVersion >= KOTLIN_1_9 -> 20
            // 1.8.0 added support for 19
            // https://kotlinlang.org/docs/whatsnew18.html#kotlin-jvm
            pluginVersion >= KOTLIN_1_8 -> 19
            // 1.6.0 added support for 17
            // https://kotlinlang.org/docs/whatsnew16.html#kotlin-jvm
            pluginVersion >= KOTLIN_1_6 -> 17
            // 1.4.0 supports also 13..14
            // https://stackoverflow.com/a/64331184/1816338
            pluginVersion >= KOTLIN_1_4 -> 14
            // 1.3.30 added support for 9..12.
            // https://blog.jetbrains.com/kotlin/2019/04/kotlin-1-3-30-released/#SpecifyingJVMbytecodetargets9%E2%80%9312
            pluginVersion >= KOTLIN_1_3_30 -> 12
            else -> 8
        }
        if (this > maxSupportedTarget) {
            return maxSupportedTarget
        }
    }
    return this
}
