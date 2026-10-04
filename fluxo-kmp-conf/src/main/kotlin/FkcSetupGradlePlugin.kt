@file:Suppress("LongParameterList")
@file:JvmName("Fkc")
@file:JvmMultifileClass

import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.fluxoConfiguration
import fluxo.conf.pub.gradlePluginExt
import fluxo.log.e
import fluxo.log.l
import fluxo.log.w
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

// TODO: Support for multiple plugins

/**
 * Lazily configures a Gradle Plugin module (Gradle [Project]).
 *
 * @receiver The [Project] to configure.
 *
 * @param pluginName The name of the Gradle plugin.
 * @param pluginClass The class of the Gradle plugin.
 * @param displayName The display name of the Gradle plugin.
 * @param group The group name of the Gradle plugin.
 * @param tags The tags of the Gradle plugin.
 * @param pluginId The ID of the Gradle plugin.
 *
 * @param kotlin Configuration block for the [KotlinJvmProjectExtension].
 * @param config Configuration block for the [FluxoConfigurationExtension].
 *
 * @see org.gradle.plugin.devel.PluginDeclaration
 */
@JvmName("setupGradlePlugin")
public fun Project.fkcSetupGradlePlugin(
    pluginName: String? = null,
    pluginClass: String? = null,
    displayName: String? = null,
    group: String? = this.group.toString().takeIf { it.isNotBlank() },
    tags: List<String>? = null,
    pluginId: String? = if (pluginName != null && group != null) "$group.$pluginName" else null,
    kotlin: (KotlinJvmProjectExtension.() -> Unit)? = null,
    config: (FluxoConfigurationExtension.() -> Unit)? = null,
) {
    val project = this
    project.fluxoConfiguration c@{
        configureAsLibrary()

        pluginName?.let { projectName = it }

        config?.invoke(this)

        if (kotlin != null) {
            asGradlePlugin {
                this.kotlin(kotlin)
            }
        } else {
            asGradlePlugin()
        }

        pluginName ?: return@c

        // `gradlePlugin`: Configure Gradle plugin eagerly!
        // Otherwise, it's not available for composite builds.
        project.declareGradlePlugin(
            pluginName = pluginName,
            pluginId = pluginId,
            pluginClass = pluginClass,
            displayName = displayName,
            description = this@c.description,
            tags = tags,
        )
    }
}

private fun Project.declareGradlePlugin(
    pluginName: String,
    pluginId: String?,
    pluginClass: String?,
    displayName: String?,
    description: String?,
    tags: List<String>?,
) {
    gradlePluginExt.plugins.maybeCreate(pluginName).apply {
        // Gradle 9.4+ pre-fills a new declaration's id with its name, so an id equal to
        // the name means "not set" there; earlier Gradle leaves it null.
        // Without the name check, the requested plugin ID is silently dropped and the
        // plugin is published under the bare declaration name.
        if (id.isNullOrBlank() || id == name) {
            if (pluginId.isNullOrEmpty()) {
                logger.w("Plugin ID is not set for plugin '$pluginName'!")
            } else {
                id = pluginId
                logger.l("Plugin '$pluginName' prepared with ID '$pluginId'")
            }
        }

        pluginClass?.let { implementationClass = it }
        displayName?.let { this.displayName = it }
        description?.let { this.description = it }

        if (!tags.isNullOrEmpty()) {
            try {
                @Suppress("UnstableApiUsage")
                this.tags.set(tags)
            } catch (e: Throwable) {
                logger.e("Failed to set tags for plugin $pluginName", e)
            }
        }
    }
}
