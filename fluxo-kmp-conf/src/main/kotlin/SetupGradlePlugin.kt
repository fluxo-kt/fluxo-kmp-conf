import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.fluxoConfiguration
import org.gradle.api.Project

public fun Project.setupGradlePlugin(
    pluginName: String? = null,
    group: String? = null,
    version: String? = null,
    pluginId: String? = if (pluginName != null && group != null) "$group.$pluginName" else null,
    config: (FluxoConfigurationExtension.() -> Unit)? = null,
    body: (KotlinSingleTarget.() -> Unit)? = null,
): Unit = fluxoConfiguration {
    config?.invoke(this)

    configureAsGradlePlugin {
        if (body != null) {
            @Suppress("UNCHECKED_CAST")
            kotlin { body(this as KotlinSingleTarget) }
        }
    }
}
