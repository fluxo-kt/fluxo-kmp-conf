@file:Suppress("LongParameterList", "ReturnCount")

package fluxo.conf.deps

import fluxo.conf.FluxoKmpConfContext
import fluxo.log.SHOW_DEBUG_LOGS
import fluxo.log.d
import fluxo.log.e
import fluxo.log.v
import fluxo.log.w
import fluxo.vc.p
import fluxo.vc.v
import getGradlePluginMarkerArtifactMavenCoordinates
import java.util.regex.Pattern
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
    catalogPluginIds: Array<out String>? = null,
    project: Project = rootProject,
    lookupClassName: Boolean = LOOKUP_CLASS_NAME_IN_CLASS_LOADER,
    canLoadDynamically: Boolean = true,
): ApplyPluginResult {
    val logger = project.logger
    val pluginManager = project.pluginManager
    if (pluginManager.hasPlugin(id)) {
        logger.v("Project has plugin '$id' already applied in '${project.path}'")
        return ApplyPluginResult.TRUE
    }

    try {
        pluginManager.apply(id)
        logger.d("Plugin '$id' is applied dynamically by id in '${project.path}'")
        return ApplyPluginResult.TRUE
    } catch (e: Throwable) {
        @Suppress("InstanceOfCheckForException")
        if (project.hasPluginAvailable(id) || e !is UnknownPluginException) {
            logger.e("Failed to apply plugin '$id' in '${project.path}': $e", e)
        }
    }

    // Get the plugin ID and version with all the fallbacks from version catalog.
    val (pluginId, pluginVersion, catalogPluginAlias) = getPluginIdAndVersion(
        id = id,
        version = version,
        catalogPluginIds = catalogPluginIds,
        catalogPluginId = catalogPluginId,
        logger = logger,
        catalogVersionIds = catalogVersionIds,
        catalogVersionId = catalogVersionId,
    )

    val pluginClass = loadPluginArtifactAndGetClass(
        pluginId = pluginId,
        pluginVersion = pluginVersion,
        className = className,
        catalogPluginAlias = catalogPluginAlias,
        lookupClassName = lookupClassName,
        canLoadDynamically = canLoadDynamically,
        project = project,
    ) ?: return ApplyPluginResult(applied = false, id = id, alias = catalogPluginAlias)

    pluginManager.apply(pluginClass)

    if (SHOW_DEBUG_LOGS) {
        check(pluginManager.hasPlugin(id)) {
            "Plugin '$id' was not dynamically applied in '${project.path}'!"
        }
    }

    return ApplyPluginResult(applied = true, pluginClass = pluginClass, id = id)
}

@Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth")
private fun FluxoKmpConfContext.loadPluginArtifactAndGetClass(
    pluginId: String,
    pluginVersion: String?,
    className: String?,
    catalogPluginAlias: String?,
    lookupClassName: Boolean,
    canLoadDynamically: Boolean,
    project: Project,
): Class<*>? {
    val logger = project.logger
    val example = loadingWarnExample(pluginId, catalogPluginAlias)
    val classNames: MutableSet<String>
    if (!className.isNullOrEmpty()) {
        classNames = mutableSetOf(className)
    } else {
        if (!lookupClassName) {
            val error = "Can't load plugin '$pluginId' dynamically" +
                " in '${project.path}' (unknown plugin class name)!"
            logger.e(loadingErrorMessage(error, example))
            return null
        }
        classNames = mutableSetOf()
    }

    // Try the classpath first.
    var pluginClass: Class<*>? = tryGetClassForName(className)
    if (pluginClass != null) {
        logger.v(
            "Found plugin '$pluginId' class on the classpath for '${project.path}': $className",
        )
        return pluginClass
    }
    if (lookupClassName && classNames.isEmpty()) {
        try {
            val classLoader = Thread.currentThread().contextClassLoader ?: javaClass.classLoader
            classNames += classLoader.findPluginClassNames(pluginId, logger)
            for (name in classNames) {
                pluginClass = tryGetClassForName(className)
                if (pluginClass != null) {
                    val message = "Found plugin '$pluginId' class on the classpath" +
                        " for '${project.path}': $className" +
                        CLASS_NAME_AUTO_DETECTED
                    logger.w(message)
                    return pluginClass
                }
            }
        } catch (e: Throwable) {
            val message = "Unexpected error while dynamically loading plugin '$pluginId'" +
                " in '${project.path}': $e"
            logger.e(message, e)
        }
    }

    if (!canLoadDynamically) {
        val error = "Can't load plugin '$pluginId' dynamically in '${project.path}'!"
        logger.e(loadingErrorMessage(error, example))
        return null
    }
    val coords = getGradlePluginMarkerArtifactMavenCoordinates(pluginId, pluginVersion)
    try {
        val classLoader = JarState.from(coords, provisioner).classLoader
        var detected = ""
        if (classNames.isEmpty()) {
            classNames += classLoader.findPluginClassNames(pluginId, logger)
            detected = CLASS_NAME_AUTO_DETECTED
        }
        for (name in classNames) {
            pluginClass = Class.forName(className, true, classLoader)
            if (pluginClass != null) {
                val confFile = "build.gradle.kts"
                val warn = "Dynamically loaded plugin '$pluginId'" +
                    " in '${project.path}' from [$coords]$detected.\n " +
                    "You may want to add it to the classpath in the root $confFile instead! " +
                    example
                logger.w(warn)
                return pluginClass
            }
        }

        error("plugin class name is unknown and wasn't detected for '${project.path}'")
    } catch (e: Throwable) {
        val error = "Couldn't load plugin '$pluginId' dynamically" +
            " in '${project.path}' from [$coords]: $e"
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

internal fun tryGetClassForName(className: String?, classLoader: ClassLoader? = null): Class<*>? {
    if (className.isNullOrEmpty()) {
        return null
    }
    return try {
        when (classLoader) {
            null -> Class.forName(className)
            else -> Class.forName(className, true, classLoader)
        }
    } catch (_: ClassNotFoundException) {
        null
    }
}

@Suppress("NestedBlockDepth")
private fun FluxoKmpConfContext.getPluginIdAndVersion(
    id: String,
    version: String?,
    catalogPluginIds: Array<out String>? = null,
    catalogPluginId: String?,
    logger: Logger,
    catalogVersionIds: Array<out String>?,
    catalogVersionId: String?,
): Triple<String, String?, String?> {
    var pluginId = id
    var pluginVersion = version
    var catalogPluginAlias: String? = null
    val libs = libs

    val (p, alias) = libs.p(catalogPluginIds)
        ?: (libs.p(catalogPluginId) to catalogPluginId)
    if (p != null) {
        val pId = p.pluginId
        if (pId != pluginId) {
            logger.e("Plugin '$pluginId' has unexpected id in version catalog: '$pId'")
        }
        pluginId = pId
        // TODO: Check version for correctness
        pluginVersion = p.version.toString()
        catalogPluginAlias = alias
    } else {
        libs.v(catalogVersionIds) ?: libs.v(catalogVersionId)?.let {
            pluginVersion = it
        }

        // Find actual plugin alias in the version catalog.
        for (pAlias in libs.gradle?.pluginAliases.orEmpty()) {
            val pp = libs.p(pAlias)
            if (pp?.pluginId != id) {
                continue
            }

            // TODO: Check version for correctness
            pluginVersion = pp.version.toString()
            catalogPluginAlias = pAlias
            break
        }
    }

    return Triple(pluginId, pluginVersion, catalogPluginAlias)
}

private fun Project.hasPluginAvailable(pluginId: String): Boolean {
    val plugin = buildscript.configurations.getByName("classpath")
        .resolvedConfiguration.resolvedArtifacts
        .find { it.moduleVersion.id.name.startsWith(pluginId) }
    return plugin != null
}

private const val CLASS_NAME_AUTO_DETECTED = " (class name is not provided and auto detected!)"

internal fun Project.loadPluginStaticallyError(
    pluginId: String,
    catalogPluginAlias: String? = null,
) {
    val example = loadingWarnExample(pluginId, catalogPluginAlias)
    val error = "Can't load plugin '$pluginId' dynamically in '$path'!"
    logger.e(loadingErrorMessage(error, example))
}

private fun loadingErrorMessage(err: String, example: String) = "$err\n " +
    "Please, add it to the classpath in the root or module build.gradle.kts! $example"

private fun loadingWarnExample(pluginId: String, catalogPluginAlias: String?): String {
    val declaration = when {
        catalogPluginAlias.isNullOrEmpty() -> "id(\"$pluginId\")"
        else -> "alias(libs.plugins.${aliasNormalized(catalogPluginAlias)})"
    }
    return """
        Example:
        ```
        plugins {
            ...
            $declaration apply false
            ...
        }
        ```
    """.trimIndent()
}

internal class ApplyPluginResult(
    val applied: Boolean,
    val id: String? = null,
    val alias: String? = null,
    val pluginClass: Class<*>? = null,
) {
    fun orThrow() {
        if (!applied) {
            val example = loadingWarnExample(id.orEmpty(), alias)
            val error = "Plugin '$id' is required but was not applied!"
            error(loadingErrorMessage(error, example))
        }
    }

    internal companion object {
        val TRUE = ApplyPluginResult(true)
    }
}

private val SEPARATOR = Pattern.compile("[_.-]")

/** @see org.gradle.api.internal.catalog.AliasNormalizer */
private fun aliasNormalized(name: String): String {
    return SEPARATOR.matcher(name).replaceAll(".")
}

private const val LOOKUP_CLASS_NAME_IN_CLASS_LOADER = true
