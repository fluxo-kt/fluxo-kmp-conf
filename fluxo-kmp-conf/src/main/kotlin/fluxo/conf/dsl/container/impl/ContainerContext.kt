package fluxo.conf.dsl.container.impl

import fluxo.conf.FluxoKmpConfContext
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory

internal abstract class ContainerContext(
    val context: FluxoKmpConfContext,
    project: Project,
) {
    val kotlinPluginVersion: KotlinVersion get() = context.kotlinPluginVersion

    val objects: ObjectFactory = project.objects
}
