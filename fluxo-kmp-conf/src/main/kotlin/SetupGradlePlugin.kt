import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.fluxoConfiguration
import fluxo.conf.feat.gradlePlugin
import fluxo.conf.impl.e
import fluxo.conf.impl.l
import fluxo.conf.impl.w
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

public fun Project.setupGradlePlugin(
    pluginName: String? = null,
    pluginClass: String? = null,
    displayName: String? = null,
    group: String? = this.group.toString().takeIf { it.isNotBlank() },
    tags: List<String>? = null,
    pluginId: String? = if (pluginName != null && group != null) "$group.$pluginName" else null,
    kotlin: (KotlinJvmProjectExtension.() -> Unit)? = null,
    config: (FluxoConfigurationExtension.() -> Unit)? = null,
): Unit = fluxoConfiguration c@{
    explicitApi()

    kotlin?.let { kotlinJvmConfiguration ->
        onConfiguration {
            // TODO: Can we avoid this cast?
            (this as KotlinJvmProjectExtension).kotlinJvmConfiguration()
        }
    }

    config?.invoke(this)

    asGradlePlugin()

    pluginName ?: return@c
    onConfiguration {
        gradlePlugin.apply {
            plugins.create(pluginName) {
                // TODO: Retry create pluginId from configuration if null?

                val logger = logger
                if (pluginId.isNullOrEmpty()) {
                    logger.w("Plugin ID is not set for plugin '$pluginName'!")
                } else {
                    id = pluginId
                    logger.l("Plugin '$pluginName' prepared with ID '$pluginId'")
                }

                pluginClass?.let { implementationClass = it }
                displayName?.let { this.displayName = it }
                this@c.description?.let { this.description = it }

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
    }
}
