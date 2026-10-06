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

internal val KOTLIN_2_0 = KotlinVersion(2, 0, 0)

internal val KOTLIN_2_0_20 = KotlinVersion(2, 0, 20)

internal val KOTLIN_2_1 = KotlinVersion(2, 1, 0)

internal val KOTLIN_2_2 = KotlinVersion(2, 2, 0)

internal val KOTLIN_2_3 = KotlinVersion(2, 3, 0)

internal val KOTLIN_2_4 = KotlinVersion(2, 4, 0)

internal val KOTLIN_2_4_20 = KotlinVersion(2, 4, 20)

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

/** Gradle property opting out of the [kotlinStdlibSkewError] fail-fast guard. */
internal const val ALLOW_KOTLIN_STDLIB_SKEW_PROP = "fluxo.allowKotlinStdlibSkew"

/**
 * Returns an actionable error message when [stdlibVersion] (`kotlin-stdlib` /
 * `coreLibrariesVersion`) is **strictly newer** than [compilerVersion] (the
 * Kotlin Gradle plugin), or `null` when they are compatible.
 *
 * A runtime newer than the compiler makes Kotlin emit a version-mismatch
 * warning that `allWarningsAsErrors` turns fatal on CI/release — the footgun two
 * independently-pinned catalog versions (`kotlin` vs `kotlinCoreLibraries`)
 * invite. An equal or older stdlib is fine on the JVM, which supports its own
 * and older runtimes, so the check is strictly directional, not equality.
 * Kotlin/JS and Kotlin/Wasm accept only the compiler's exact stdlib, which
 * `useCompilerStdlib` gives them.
 *
 * [stdlibVersion] is parsed leniently via [parseKotlinPluginVersion]; a blank or
 * unparseable value yields `null` (skip) rather than masking a real build behind
 * a parser crash.
 */
internal fun kotlinStdlibSkewError(
    compilerVersion: KotlinVersion,
    stdlibVersion: String?,
): String? {
    val stdlib = stdlibVersion?.takeIf { it.isNotBlank() }
        ?.let { runCatching { parseKotlinPluginVersion(it) }.getOrNull() }
    if (stdlib == null || stdlib <= compilerVersion) return null
    return "Kotlin stdlib ($stdlib) is newer than the compiler ($compilerVersion). " +
        "Lower `kotlinCoreLibraries` to the `kotlin` version " +
        "(libs.versions.toml / fkcSetup DSL), or pass " +
        "-P$ALLOW_KOTLIN_STDLIB_SKEW_PROP=true. A runtime newer than the compiler " +
        "is fatal under allWarningsAsErrors on CI/release."
}

