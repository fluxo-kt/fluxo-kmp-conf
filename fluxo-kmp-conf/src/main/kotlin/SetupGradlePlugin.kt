import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.fluxoConfiguration
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

public fun Project.setupGradlePlugin(
    pluginName: String? = null,
    group: String? = null,
    version: String? = null,
    pluginId: String? = if (pluginName != null && group != null) "$group.$pluginName" else null,
    // FIXME: Find a better API for this, ideally from within the `config` block.
    kotlin: (KotlinJvmProjectExtension.() -> Unit)? = null,
    config: (FluxoConfigurationExtension.() -> Unit)? = null,
): Unit = fluxoConfiguration {
    explicitApi()

    // FIXME: Configuration should be lazy, skippable, and only applied if the target is configured.
    config?.invoke(this)

    asGradlePlugin {
        kotlin?.let { this.kotlin(action = it) }
    }
}
