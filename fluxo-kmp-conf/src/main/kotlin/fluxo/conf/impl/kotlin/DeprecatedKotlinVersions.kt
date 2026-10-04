package fluxo.conf.impl.kotlin

import fluxo.conf.BuildEndReport
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicBoolean
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion as KotlinLangVersion

/**
 * `true` when the consumer's own Kotlin Gradle plugin marks this language/API version deprecated:
 * it still compiles, with a `DEPRECATED_LANGUAGE_VERSION` warning.
 *
 * KGP puts a warning-level `@Deprecated` on exactly the enum entries its compiler deprecates
 * (the compiler's `LanguageVersion` range from `FIRST_SUPPORTED` up to `FIRST_NON_DEPRECATED`;
 * matched for KGP 2.1.21, 2.2.21, 2.4.0 and 2.4.20, read 2026-10-04), so the answer always comes
 * from the Kotlin the consumer runs, with no version table to maintain. Reflection because the
 * annotation is the only carrier of this fact; error level means unsupported, which the compiler
 * rejects on its own.
 */
internal val KotlinLangVersion.isDeprecatedByKgp: Boolean
    get() = try {
        KotlinLangVersion::class.java.getField(name)
            .getAnnotation(Deprecated::class.java)?.level == DeprecationLevel.WARNING
    } catch (_: NoSuchFieldException) {
        false
    }

/**
 * Modules compiled at a language/API version their Kotlin deprecates, reported once per build.
 *
 * Each such compilation gets [KotlinDefault.SUPPRESS_VERSION_WARNINGS], so the build keeps
 * passing; without a report the consumer would never learn the version is on its way out.
 */
internal class DeprecatedKotlinVersions(
    private val report: BuildEndReport,
    private val kotlinPluginVersion: KotlinVersion,
) {
    /** Sorted maps and sets keep the warning text identical between builds. */
    private val modulesByVersion = ConcurrentSkipListMap<KotlinLangVersion, MutableSet<String>>()
    private val reported = AtomicBoolean()

    fun record(version: KotlinLangVersion, projectPath: String) {
        modulesByVersion.computeIfAbsent(version) { ConcurrentSkipListSet() } += projectPath
        if (reported.compareAndSet(false, true)) report.warn(::message)
    }

    private fun message(): String = modulesByVersion.entries.joinToString("\n") { (v, paths) ->
        val moveTo = KotlinLangVersion.values()
            .firstOrNull { it > v && !it.isDeprecatedByKgp }?.version ?: "a newer version"
        "Kotlin language/API version ${v.version} is deprecated by Kotlin $kotlinPluginVersion " +
            "in ${paths.joinToString { "'$it'" }}. fluxo-kmp-conf suppresses that compiler " +
            "warning so " +
            "builds with warnings as errors keep passing. Move to $moveTo or newer " +
            "(kotlinLangVersion / kotlinApiVersion)."
    }
}
