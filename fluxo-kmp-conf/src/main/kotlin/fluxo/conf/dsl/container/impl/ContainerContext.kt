package fluxo.conf.dsl.container.impl

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory

internal open class ContainerContext(
    configuration: FluxoConfigurationExtensionImpl,
    val context: FluxoKmpConfContext = configuration.context,
    val project: Project = configuration.project,
) {
    val fluxoConfiguration: FluxoConfigurationExtension = configuration

    val kotlinPluginVersion: KotlinVersion get() = context.kotlinPluginVersion

    val objects: ObjectFactory = project.objects
}
