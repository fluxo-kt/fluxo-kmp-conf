@file:Suppress("LongParameterList", "ReturnCount")

package fluxo.conf.deps

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.impl.SHOW_DEBUG_LOGS
import fluxo.conf.impl.d
import fluxo.conf.impl.e
import fluxo.conf.impl.p
import fluxo.conf.impl.v
import fluxo.conf.impl.w
import getGradlePluginMarkerArtifactMavenCoordinates
import org.gradle.api.Project
import org.gradle.api.logging.Logger
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

    // Get the plugin ID and version with all the fallbacks from version catalog.
    val (pluginId, pluginVersion) = getPluginIdAndVersion(
        id,
        version,
        catalogPluginIds,
        catalogPluginId,
        logger,
        catalogVersionIds,
        catalogVersionId,
    )

    val pluginClass = loadPluginArtifactAndGetClass(pluginId, pluginVersion, className)
        ?: return ApplyPluginResult.FALSE

    pluginManager.apply(pluginClass)

    if (SHOW_DEBUG_LOGS) {
        check(pluginManager.hasPlugin(id)) {
            "Plugin '$id' was not dynamically applied!"
        }
    }

    return ApplyPluginResult(applied = true, pluginClass = pluginClass)
}

private fun FluxoKmpConfContext.loadPluginArtifactAndGetClass(
    pluginId: String,
    pluginVersion: String?,
    className: String?,
): Class<*>? {
    val logger = rootProject.logger
    val example = loadingWarnExample(pluginId)
    val classNames: MutableSet<String>
    if (!className.isNullOrEmpty()) {
        classNames = mutableSetOf(className)
    } else {
        if (!LOOKUP_CLASS_NAME_IN_CLASS_LOADER) {
            val error = "Can't load plugin '$pluginId' dynamically (unknown plugin class name)!"
            logger.e(loadingErrorMessage(error, example))
            return null
        }
        classNames = mutableSetOf()
    }

    // Try the classpath first.
    var pluginClass: Class<*>? = tryGetClassForName(className)
    if (pluginClass != null) {
        logger.v("Found plugin '$pluginId' class on the classpath: $className")
        return pluginClass
    }
    if (LOOKUP_CLASS_NAME_IN_CLASS_LOADER && classNames.isEmpty()) {
        try {
            val classLoader = Thread.currentThread().contextClassLoader ?: javaClass.classLoader
            classNames += classLoader.findPluginClassNames(pluginId, logger)
            for (name in classNames) {
                pluginClass = tryGetClassForName(className)
                if (pluginClass != null) {
                    val message = "Found plugin '$pluginId' class on the classpath: $className" +
                        CLASS_NAME_NOT_PROVIDED
                    logger.w(message)
                    return pluginClass
                }
            }
        } catch (e: Throwable) {
            logger.e("Unexpected error while dynamically loading plugin '$pluginId': $e", e)
        }
    }

    val coords = getGradlePluginMarkerArtifactMavenCoordinates(pluginId, pluginVersion)
    try {
        val classLoader = JarState.from(coords, provisioner).classLoader
        var detected = ""
        if (classNames.isEmpty()) {
            classNames += classLoader.findPluginClassNames(pluginId, logger)
            detected = CLASS_NAME_NOT_PROVIDED
        }
        for (name in classNames) {
            pluginClass = classLoader.loadClass(className)
            if (pluginClass != null) {
                val warn = "Dynamically loaded plugin '$pluginId' from [$coords]$detected.\n " +
                    "You may want to add it to the classpath in the root build.gradle.kts instead! $example"
                logger.w(warn)
                return pluginClass
            }
        }

        error("plugin class name is unknown and wasn't detected")
    } catch (e: Throwable) {
        val error = "Couldn't load plugin '$pluginId' dynamically from [$coords]: $e"
        logger.e(loadingErrorMessage(error, example), e)
    }
    return null
}

private fun ClassLoader.findPluginClassNames(
    pluginId: String,
    logger: Logger,
): List<String> = sequence {
    val files = getResources("META-INF\\gradle-plugins\\$pluginId.properties")
    for (url in files) {
        url.openStream().use { stream ->
            stream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val l = line.trimStart()
                    if (!l.startsWith(IMPLEMENTATION_CLASS, ignoreCase = true)) {
                        continue
                    }
                    val className = l.substring(IMPLEMENTATION_CLASS.length).trim()
                        .takeIf { it.isNotEmpty() }
                        ?: continue
                    if (SHOW_DEBUG_LOGS) {
                        val msg = "Detected plugin '$pluginId' class name: '$className' (via $url)"
                        logger.v(msg)
                    }
                    yield(className)
                }
            }
        }
    }
}.toList() // always terminate the sequence

private const val IMPLEMENTATION_CLASS = "implementation-class="

private fun tryGetClassForName(className: String?): Class<*>? {
    if (className.isNullOrEmpty()) {
        return null
    }
    return try {
        Class.forName(className)
    } catch (_: ClassNotFoundException) {
        null
    }
}

private fun FluxoKmpConfContext.getPluginIdAndVersion(
    id: String,
    version: String?,
    catalogPluginIds: Array<out String>,
    catalogPluginId: String?,
    logger: Logger,
    catalogVersionIds: Array<out String>?,
    catalogVersionId: String?,
): Pair<String, String?> {
    var pluginId = id
    var pluginVersion = version
    libs?.let { libs ->
        val p = libs.p(catalogPluginIds)?.orNull
            ?: libs.p(catalogPluginId)?.orNull
        if (p != null) {
            val pId = p.pluginId
            if (pId != pluginId) {
                logger.e("Plugin '$pluginId' has unexpected id in version catalog: '$pId'")
            }
            pluginId = pId
            // TODO: Check version for correctness
            pluginVersion = p.version.toString()
        } else {
            libs.v(catalogVersionIds) ?: libs.v(catalogVersionId)?.let {
                pluginVersion = it
            }
        }
    }
    return pluginId to pluginVersion
}

private fun Project.hasPluginAvailable(pluginId: String): Boolean {
    val plugin = buildscript.configurations.getByName("classpath")
        .resolvedConfiguration.resolvedArtifacts
        .find { it.moduleVersion.id.name.startsWith(pluginId) }
    return plugin != null
}

private const val CLASS_NAME_NOT_PROVIDED = " (class name is not provided and auto detected!)"

private fun loadingErrorMessage(err: String, example: String) = "$err\n " +
    "Please, add it to the classpath in the root or module build.gradle.kts! $example"

private fun loadingWarnExample(pluginId: String) = """
        Example:
        ```
        plugins {
            ...
            id("$pluginId") apply false
            ...
        }
        ```
    """.trimIndent()

internal class ApplyPluginResult(
    val applied: Boolean,
    val pluginClass: Class<*>? = null,
) {
    internal companion object {
        val FALSE = ApplyPluginResult(false)
        val TRUE = ApplyPluginResult(true)
    }
}

private const val LOOKUP_CLASS_NAME_IN_CLASS_LOADER = true
