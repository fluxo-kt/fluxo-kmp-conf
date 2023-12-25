package fluxo.conf.dsl.container.impl

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory

internal open class ContainerContext(
    val conf: FluxoConfigurationExtensionImpl,
) {
    val project: Project get() = conf.project

    val ctx: FluxoKmpConfContext get() = conf.ctx

    val kotlinPluginVersion: KotlinVersion get() = ctx.kotlinPluginVersion

    val objects: ObjectFactory get() = project.objects
}
