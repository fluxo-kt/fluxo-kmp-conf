import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.fluxoConfiguration
import fluxo.conf.feat.gradlePluginExt
import fluxo.conf.impl.e
import fluxo.conf.impl.l
import fluxo.conf.impl.w
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

@Suppress("LongParameterList")
public fun Project.setupGradlePlugin(
    pluginName: String? = null,
    pluginClass: String? = null,
    displayName: String? = null,
    group: String? = this.group.toString().takeIf { it.isNotBlank() },
    tags: List<String>? = null,
    pluginId: String? = if (pluginName != null && group != null) "$group.$pluginName" else null,
    kotlin: (KotlinJvmProjectExtension.() -> Unit)? = null,
    config: (FluxoConfigurationExtension.() -> Unit)? = null,
): Unit = fluxoConfiguration configuration@{
    configureAsLibrary()

    if (pluginName != null) {
        projectName = pluginName
    }

    kotlin?.let { kotlinJvmConfiguration ->
        onConfiguration {
            // TODO: Try avoid this cast?
            (this as KotlinJvmProjectExtension).kotlinJvmConfiguration()
        }
    }

    config?.invoke(this)

    asGradlePlugin()

    pluginName ?: return@configuration

    // `gradlePlugin`: Configure Gradle plugin eagerly!
    // Otherwise, it's not available for composite builds.
    gradlePluginExt.plugins.maybeCreate(pluginName).apply {
        // TODO: Retry create pluginId from configuration if null?

        if (id.isNullOrBlank()) {
            val logger = logger
            if (pluginId.isNullOrEmpty()) {
                logger.w("Plugin ID is not set for plugin '$pluginName'!")
            } else {
                id = pluginId
                logger.l("Plugin '$pluginName' prepared with ID '$pluginId'")
            }
        }

        pluginClass?.let { implementationClass = it }
        displayName?.let { this.displayName = it }
        this@configuration.description?.let { this.description = it }

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

// FIXME: detekt plugins https://detekt.dev/marketplace/
// FIXME: Disambiguate existing javadoc and sources tasks
// FIXME: Check BuildConfig tasks (not called on IDE sync!)
// FIXME: Spotless setup
// FIXME: git hooks
// FIXME: check all features
// FIXME: https://github.com/topjohnwu/libsu/blob/01570d643af91b0e271de018465a219eed8db322/service/build.gradle.kts#L21
//
// val context = (this as FluxoConfigurationExtensionImpl).context
// setupGradlePublishPlugin(context)
