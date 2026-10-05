package fluxo.conf.impl.kotlin

import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * Every Kotlin-independent KSP release (2.3.0 and newer, the line fluxo provides) calls
 * `KotlinJvmCompilerOptions.getJvmDefault()`, which only Kotlin Gradle plugin 2.2+ has: on Kotlin
 * 2.1 `kspKotlin` dies with a bare `NoSuchMethodError` mid-build. There only the Kotlin-tied
 * releases work, named `<kotlin version>-<ksp version>` (e.g. `2.1.21-2.0.2`). Checked at
 * configuration so the build stops with the fix instead.
 *
 * The KSP jar carries no version metadata, so the version is read from the jar's file name, which
 * Gradle's module cache writes as `<artifact>-<version>.jar`; anything unreadable passes.
 */
internal fun Project.checkKspFitsKotlin(kotlinPluginVersion: KotlinVersion) {
    if (kotlinPluginVersion >= KOTLIN_2_2) return
    val kspVersion = appliedKspVersion()
    if (kspVersion == null || '-' in kspVersion) return
    throw GradleException(
        "KSP $kspVersion needs Kotlin 2.2 or newer, and '$path' uses Kotlin " +
            "$kotlinPluginVersion. Declare the KSP release built for your Kotlin, e.g. " +
            "`id(\"$KSP_PLUGIN_ID\") version \"$kotlinPluginVersion-<ksp version>\"` " +
            "(https://github.com/google/ksp/releases), or move to Kotlin 2.2+.",
    )
}

private fun Project.appliedKspVersion(): String? {
    val plugin = plugins.findPlugin(KSP_PLUGIN_ID) ?: return null
    val name = runCatching { plugin.javaClass.protectionDomain?.codeSource?.location?.path }
        .getOrNull()?.substringAfterLast('/')
    return name?.takeIf { it.startsWith(KSP_JAR_PREFIX) && it.endsWith(".jar") }
        ?.removePrefix(KSP_JAR_PREFIX)?.removeSuffix(".jar")
}

private const val KSP_JAR_PREFIX = "symbol-processing-gradle-plugin-"
