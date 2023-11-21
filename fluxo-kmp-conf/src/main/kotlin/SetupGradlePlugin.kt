import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.fluxoConfiguration
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

public fun Project.setupGradlePlugin(
    pluginName: String? = null,
    group: String? = null,
    version: String? = null,
    pluginId: String? = if (pluginName != null && group != null) "$group.$pluginName" else null,
    kotlin: (KotlinJvmProjectExtension.() -> Unit)? = null,
    config: (FluxoConfigurationExtension.() -> Unit)? = null,
): Unit = fluxoConfiguration {
    explicitApi()
    config?.invoke(this)

    asGradlePlugin {
        kotlin?.let { kotlin(action = it) }
    }
}
