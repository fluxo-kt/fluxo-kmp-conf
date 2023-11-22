@file:Suppress("LongParameterList", "ReturnCount")

package fluxo.conf.deps

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.impl.SHOW_DEBUG_LOGS
import fluxo.conf.impl.d
import fluxo.conf.impl.e
import fluxo.conf.impl.p
import fluxo.conf.impl.v
import fluxo.conf.impl.w
import org.gradle.api.Project
import org.gradle.api.plugins.UnknownPluginException

internal fun FluxoKmpConfContext.loadAndApplyPluginIfNotApplied(
    id: String,
    className: String? = null,
    version: String? = null,
    catalogPluginId: String? = null,
    catalogVersionId: String? = catalogPluginId,
    catalogVersionIds: Array<out String>? = null,
    vararg catalogPluginIds: String,
    project: Project = rootProject,
): ApplyPluginResult {
    val logger = project.logger
    val pluginManager = project.pluginManager
    if (pluginManager.hasPlugin(id)) {
        logger.v("Project has plugin '$id' already applied")
        return ApplyPluginResult.TRUE
    }

    try {
        pluginManager.apply(id)
        logger.d("Plugin '$id' is applied dynamically by id")
        return ApplyPluginResult.TRUE
    } catch (e: Throwable) {
        @Suppress("InstanceOfCheckForException")
        if (project.hasPluginAvailable(id) || e !is UnknownPluginException) {
            logger.e("Failed to apply plugin '$id': $e", e)
        }
    }

    val pluginClass = loadPluginArtifactAndGetClass(
        id, className, version, catalogPluginId, catalogPluginIds,
        catalogVersionId, catalogVersionIds,
    ) ?: return ApplyPluginResult.FALSE

    pluginManager.apply(pluginClass)

    if (SHOW_DEBUG_LOGS) {
        check(pluginManager.hasPlugin(id)) {
            "Plugin '$id' was not dynamically applied!"
        }
    }

    return ApplyPluginResult(applied = true, pluginClass = pluginClass)
}

private fun FluxoKmpConfContext.loadPluginArtifactAndGetClass(
    id: String,
    className: String?,
    version: String?,
    catalogPluginId: String?,
    catalogPluginIds: Array<out String>,
    catalogVersionId: String?,
    catalogVersionIds: Array<out String>?,
): Class<*>? {
    val logger = rootProject.logger
    if (className.isNullOrEmpty()) {
        // TODO: Load the plugin class name from Jar
        logger.e("Can't load plugin '$id' dynamically as no plugin class name provided!")
        return null
    }

    // Rey the classpath first.
    var pluginClass: Class<*>?
    try {
        pluginClass = Class.forName(className)
        if (pluginClass != null) {
            logger.v("Found '$id' plugin class on classpath: $className")
            return pluginClass
        }
    } catch (_: ClassNotFoundException) {
    }

    // Get the plugin ID and version with all fallbacks from version catalog.
    var pluginId = id
    var pluginVersion = version
    libs?.let { libs ->
        val p = libs.p(catalogPluginIds)?.orNull
            ?: libs.p(catalogPluginId)?.orNull
        if (p != null) {
            val pId = p.pluginId
            if (pId != pluginId) {
                logger.e("Plugin '$pluginId' has unexpected id in version catalog: $pId")
            }
            pluginId = pId
            // TODO: Check version for correcness
            pluginVersion = p.version.toString()
        } else {
            libs.v(catalogVersionIds) ?: libs.v(catalogVersionId)?.let {
                pluginVersion = it
            }
        }
    }

    val mavenCoordinates = gradlePluginMarkerArtifactCoordinates(pluginId, pluginVersion)
    val loadingWarn = """
        Dynamically loading '$pluginId' plugin from [$mavenCoordinates].
        You may want to add it to the classpath in the root build.gradle.kts instead! Example:
        ```
        plugins {
            ...
            id("$pluginId") apply false
            ...
        }
        ```
    """.trimIndent()
    logger.w(loadingWarn)

    return try {
        pluginClass = JarState.from(mavenCoordinates, provisioner)
            .classLoader
            .loadClass(className)!!
        pluginClass
    } catch (e: Throwable) {
        logger.e("Couldn't load plugin '$id' dynamically: $e", e)
        null
    }
}

// Convention for the Gradle Plugin Marker Artifact
// https://docs.gradle.org/current/userguide/plugins.html#sec:plugin_markers
internal fun gradlePluginMarkerArtifactCoordinates(pluginId: String, version: String?): String {
    return pluginId.let {
        val v = if (!version.isNullOrBlank()) ":$version" else ""
        "$it:$it.gradle.plugin$v"
    }
}

private fun Project.hasPluginAvailable(pluginId: String): Boolean {
    val plugin = buildscript.configurations.getByName("classpath")
        .resolvedConfiguration.resolvedArtifacts
        .find { it.moduleVersion.id.name.startsWith(pluginId) }
    return plugin != null
}

internal class ApplyPluginResult(
    val applied: Boolean,
    val pluginClass: Class<*>? = null,
) {
    internal companion object {
        val FALSE = ApplyPluginResult(false)
        val TRUE = ApplyPluginResult(true)
    }
}
