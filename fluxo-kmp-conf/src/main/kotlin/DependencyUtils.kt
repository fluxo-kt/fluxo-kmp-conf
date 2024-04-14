@file:JvmName("Fkc")
@file:JvmMultifileClass

import fluxo.conf.impl.COMPILE_ONLY
import fluxo.conf.impl.addAndLog
import fluxo.conf.impl.capitalizeAsciiOnly
import org.gradle.api.artifacts.Dependency
import org.gradle.api.provider.Provider
import org.gradle.plugin.use.PluginDependency
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.Companion.COMMON_MAIN_SOURCE_SET_NAME
import org.jetbrains.kotlin.gradle.plugin.mpp.DefaultKotlinDependencyHandler

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
    val parent = (this as DefaultKotlinDependencyHandler).parent
    var confName = parent.compileOnlyConfigurationName
        .replace(COMPILE_ONLY, "", ignoreCase = true)
    if (confName.startsWith(COMMON_MAIN_SOURCE_SET_NAME, ignoreCase = true)) {
        confName += "Metadata"
    } else {
        confName = confName.replace(MAIN_SOURCE_SET_POSTFIX, "", ignoreCase = true)
    }
    confName = "ksp${confName.capitalizeAsciiOnly()}"
    return with(project) {
        dependencies.addAndLog(confName, dependencyNotation)
    }
}
