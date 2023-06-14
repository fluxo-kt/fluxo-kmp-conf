package fluxo.conf.deps

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.impl.SHOW_DEBUG_LOGS
import fluxo.conf.impl.d
import fluxo.conf.impl.p
import fluxo.conf.impl.v
import fluxo.conf.impl.w
import org.gradle.api.Project

@Suppress("LongParameterList", "ReturnCount")
internal fun FluxoKmpConfContext.loadAndApplyPluginIfNotApplied(
    id: String,
    className: String? = null,
    version: String? = null,
    catalogPluginId: String? = null,
    catalogVersionId: String? = catalogPluginId,
    catalogVersionIds: Array<out String>? = null,
    vararg catalogPluginIds: String,
) {
    val project = rootProject
    val logger = project.logger
    val pluginManager = project.pluginManager
    if (pluginManager.hasPlugin(id)) {
        logger.v("Root project has plugin '$id' already applied")
        return
    }

    try {
        pluginManager.apply(id)
        return
    } catch (e: Throwable) {
        logger.v("Failed to apply plugin '$id': $e", e)
    }

    if (className.isNullOrEmpty()) {
        logger.w("Can't load plugin '$id' dynamically as no plugin class name provided!")
        return
    }

    var pluginClass = try {
        Class.forName(className)
    } catch (_: ClassNotFoundException) {
        null
    }
    if (pluginClass != null) {
        logger.v("Found '$id' plugin class on classpath: $className")
    } else {
        var pluginId = id
        var pluginVersion = version
        val libs = libs
        val p = libs.p(catalogPluginIds)?.orNull
            ?: libs.p(catalogPluginId)?.orNull
        if (p != null) {
            val pId = p.pluginId
            if (pId != pluginId) {
                logger.w("Plugin '$pluginId' has unexpected id in version catalog: $pId")
            }
            pluginId = pId
            // TODO: Check version for correcness
            pluginVersion = p.version.toString()
        } else {
            libs.v(catalogVersionIds) ?: libs.v(catalogVersionId)?.let {
                pluginVersion = it
            }
        }

        // Standard Gradle plugin coordinates convention
        val mavenModule = "$pluginId:$pluginId.gradle.plugin"
        val mavenCoordinates = "$mavenModule:$pluginVersion"
        logger.d("Loading '$pluginId' plugin from [$mavenCoordinates]")

        // TODO: Load the plugin class name from Jar
        pluginClass = JarState.from(mavenCoordinates, provisioner)
            .classLoader
            .loadClass(className)!!
    }

    pluginManager.apply(pluginClass)

    if (SHOW_DEBUG_LOGS) {
        check(pluginManager.hasPlugin(id)) {
            "Plugin '$id' was not applied!"
        }
    }
}

private fun Project.isPluginAvailable(pluginId: String): Boolean {
    val plugin = buildscript.configurations.getByName("classpath")
        .resolvedConfiguration.resolvedArtifacts
        .find { it.moduleVersion.id.name.startsWith(pluginId) }
    return plugin != null
}
