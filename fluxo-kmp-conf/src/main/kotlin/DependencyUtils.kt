@file:JvmName("Fkc")
@file:JvmMultifileClass

import fluxo.conf.impl.COMPILE_ONLY
import fluxo.conf.impl.addAndLog
import fluxo.conf.impl.capitalizeAsciiOnly
import org.gradle.api.artifacts.Dependency
import org.gradle.api.provider.Provider
import org.gradle.plugin.use.PluginDependency
import org.jetbrains.kotlin.gradle.plugin.HasKotlinDependencies
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.Companion.COMMON_MAIN_SOURCE_SET_NAME

/**
 * Converts a [PluginDependency] to a usual module dependency
 * with Gradle Plugin Marker Artifact convention.
 */
public fun Provider<PluginDependency>.toModuleDependency(): Provider<String> =
    map(PluginDependency::toModuleDependency)

/**
 * Converts a [PluginDependency] to a usual module dependency
 * with Gradle Plugin Marker Artifact convention.
 */
public fun PluginDependency.toModuleDependency(): String =
    // TODO: Support rich VersionConstraint
    getGradlePluginMarkerArtifactMavenCoordinates(pluginId, version.toString())

// Convention for the Gradle Plugin Marker Artifact
// https://docs.gradle.org/current/userguide/plugins.html#sec:plugin_markers
internal fun getGradlePluginMarkerArtifactMavenCoordinates(
    pluginId: String,
    version: String?,
): String {
    val v = if (!version.isNullOrBlank()) ":$version" else ""
    return pluginId.let { "$it:$it.gradle.plugin$v" }
}


/**
 * Convenience function for adding a KSP dependency to the [KotlinDependencyHandler].
 */
public fun KotlinDependencyHandler.ksp(dependencyNotation: Any): Dependency? {
    // Starting from KSP 1.0.1, applying KSP on a multiplatform project requires
    // instead of writing the ksp("dep")
    // use ksp<Target>() or add(ksp<SourceSet>).
    // https://kotlinlang.org/docs/ksp-multiplatform.html
    val parent = kspParentOrNull()
        ?: error("KotlinDependencyHandler.ksp: cannot resolve source set from ${javaClass.name}")
    var confName = parent.compileOnlyConfigurationName
        .replace(COMPILE_ONLY, "", ignoreCase = true)
    if (confName.startsWith(COMMON_MAIN_SOURCE_SET_NAME, ignoreCase = true)) {
        confName += "Metadata"
    } else {
        // Literal avoids importing `internal const val MAIN_SOURCE_SET_POSTFIX` into a file that
        // also carries @Suppress("INVISIBLE_*") — triggering a Kotlin 2.2 K2 IR-optimizer crash
        // (IrConstOnlyNecessaryTransformer tries to const-fold every internal-const getter in the
        // same compilation unit regardless of which function the suppress is attached to).
        confName = confName.replace("Main", "", ignoreCase = true)
    }
    confName = "ksp${confName.capitalizeAsciiOnly()}"
    return with(project) {
        addAndLog(dependencies, confName, dependencyNotation)
    }
}

// `DefaultKotlinDependencyHandler.parent: HasKotlinDependencies` is `public final` in JVM
// bytecode; the class lives in KGP's internal `mpp` package — a compile-time restriction, not a
// runtime one. Direct typed cast gives IDE navigation and compile-time safety: a rename/move
// surfaces immediately as a compile error, not a silent runtime failure.
//
// Isolated here (not inlined into ksp()) to avoid a Kotlin 2.2 K2 IR optimizer bug that
// crashes on `internal const val` getters when @Suppress("INVISIBLE_*") is on the same function.
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
private fun KotlinDependencyHandler.kspParentOrNull(): HasKotlinDependencies? =
    (this as? org.jetbrains.kotlin.gradle.plugin.mpp.DefaultKotlinDependencyHandler)?.parent
